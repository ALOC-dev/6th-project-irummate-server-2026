package com.irummate.domain.chat.service;

import com.irummate.domain.chat.dto.ChatMemberReadCursor;
import com.irummate.domain.chat.dto.ChatMessageResponseDto;
import com.irummate.domain.chat.dto.ChatMessagesResponseDto;
import com.irummate.domain.chat.dto.ChatNotificationDto;
import com.irummate.domain.chat.dto.ChatReadResponseDto;
import com.irummate.domain.chat.dto.ChatReadEventDto;
import com.irummate.domain.chat.dto.ChatReadResult;
import com.irummate.domain.chat.dto.ChatRoomLastMessageDto;
import com.irummate.domain.chat.dto.ChatRoomListItemDto;
import com.irummate.domain.chat.dto.ChatRoomResponseDto;
import com.irummate.domain.chat.dto.ChatRoomsResponseDto;
import com.irummate.domain.chat.dto.ChatRoomUnreadCountDto;
import com.irummate.domain.chat.dto.ChatUnreadCountResponseDto;
import com.irummate.domain.chat.entity.ChatMessage;
import com.irummate.domain.chat.entity.ChatRoom;
import com.irummate.domain.chat.entity.ChatRoomMember;
import com.irummate.domain.chat.entity.ChatRoomType;
import com.irummate.domain.chat.repository.ChatMessageRepository;
import com.irummate.domain.chat.repository.ChatRoomMemberRepository;
import com.irummate.domain.chat.repository.ChatRoomRepository;
import com.irummate.domain.matching.entity.MatchRequests;
import com.irummate.domain.matching.entity.MatchStatus;
import com.irummate.domain.matching.repository.MatchRepository;
import com.irummate.domain.user.entity.UserRole;
import com.irummate.domain.user.entity.UserStatus;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.util.HashIdsUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.irummate.domain.matching.util.MatchingDtoMapper.toCardStatus;

@Service
public class ChatService {

    private static final int MIN_MESSAGE_PAGE_SIZE = 1;
    private static final int MAX_MESSAGE_PAGE_SIZE = 100;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomAccessService chatRoomAccessService;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UsersRepository usersRepository;
    private final HashIdsUtils hashIdsUtils;
    private final MatchRepository matchRepository;

    public ChatService(ChatRoomRepository chatRoomRepository,
                       ChatRoomAccessService chatRoomAccessService,
                       ChatRoomMemberRepository chatRoomMemberRepository,
                       ChatMessageRepository chatMessageRepository,
                       UsersRepository usersRepository,
                       HashIdsUtils hashIdsUtils, MatchRepository matchRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatRoomAccessService = chatRoomAccessService;
        this.chatRoomMemberRepository = chatRoomMemberRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.usersRepository = usersRepository;
        this.hashIdsUtils = hashIdsUtils;
        this.matchRepository = matchRepository;
    }

    @Transactional(readOnly = true)
    public ChatRoomsResponseDto getChatRooms(Long userId) {
        requireActiveUser(userId);
        List<ChatRoomListItemDto> roomItems = chatRoomRepository.findRoomListItemsByUserId(userId);

        List<Long> roomIds = roomItems.stream()
                .map(ChatRoomListItemDto::roomId)
                .toList();

        if (roomIds.isEmpty()) {
            return new ChatRoomsResponseDto(List.of());
        }

        // 방별 마지막 메시지와 안 읽은 개수를 일괄 조회해서 목록 조회 시 반복 쿼리를 줄인다.
        Map<Long, ChatRoomLastMessageDto> lastMessageMap = chatMessageRepository.findLastMessagesByRoomIds(roomIds)
                .stream()
                .collect(Collectors.toMap(
                        ChatRoomLastMessageDto::roomId,
                        Function.identity()
                ));

        Map<Long, Long> unreadCountMap = chatMessageRepository.countUnreadByRoomIds(roomIds, userId)
                .stream()
                .collect(Collectors.toMap(
                        ChatRoomUnreadCountDto::roomId,
                        ChatRoomUnreadCountDto::unreadCount
                ));

        List<ChatRoomResponseDto> rooms = roomItems.stream()
                .map(room -> {
                    ChatRoomLastMessageDto lastMessage = lastMessageMap.get(room.roomId());
                    Long unreadCount = unreadCountMap.getOrDefault(room.roomId(), 0L);
                    boolean matchRoom = room.type() == ChatRoomType.MATCH;
                    var recruitment = matchRoom ? null : new ChatRoomResponseDto.RecruitmentInfo(
                            room.recruitmentId(), room.recruitmentTitle(), room.recruitmentCategory(), room.participantCount());

                    return new ChatRoomResponseDto(
                            room.roomId(),
                            room.type(),
                            matchRoom ? hashIdsUtils.encode(room.partnerId()) : null,
                            room.partnerName(),
                            room.partnerProfileImageUrl(),
                            lastMessage == null ? null : lastMessage.lastMessage(),
                            lastMessage == null ? null : lastMessage.lastMessageTime(),
                            unreadCount.intValue(),
                            room.status(),
                            matchRoom ? toCardStatus(room.myMatchStatus(), room.partnerMatchStatus()) : null,
                            matchRoom ? isConfirmedByMe(room.myMatchStatus()) : null,
                            matchRoom ? canConfirm(room.myMatchStatus(), room.partnerMatchStatus()) : null,
                            recruitment
                    );
                })
                .sorted(Comparator
                        .comparing(
                                ChatRoomResponseDto::getLastMessageTime,
                                Comparator.nullsLast(Comparator.reverseOrder())
                        )
                        .thenComparing(ChatRoomResponseDto::getRoomId, Comparator.reverseOrder()))
                .toList();

        return new ChatRoomsResponseDto(rooms);
    }

    private boolean isConfirmedByMe(MatchStatus myMatchStatus) {
        return myMatchStatus == MatchStatus.FINAL_CONFIRMED;
    }

    private boolean canConfirm(MatchStatus myMatchStatus, MatchStatus partnerMatchStatus) {
        return myMatchStatus == MatchStatus.HEART
                && (partnerMatchStatus == MatchStatus.HEART
                || partnerMatchStatus == MatchStatus.FINAL_CONFIRMED);
    }

    @Transactional(readOnly = true)
    public ChatMessagesResponseDto getMessages(Long roomId, Long userId, Long cursor, int size) {
        requireActiveUser(userId);
        chatRoomAccessService.requireCurrentMember(roomId, userId);
        validateMessagePageSize(size);

        // size + 1개를 조회해서 다음 페이지가 있는지 판단한다.
        PageRequest pageRequest = PageRequest.of(0, size + 1);

        List<ChatMessage> messages = cursor == null
                ? chatMessageRepository.findByRoom_IdOrderByIdDesc(roomId, pageRequest)
                : chatMessageRepository.findByRoom_IdAndIdLessThanOrderByIdDesc(roomId, cursor, pageRequest);

        if (messages.isEmpty()) {
            return new ChatMessagesResponseDto(List.of(), false);
        }

        boolean matchRoom = getChatRoom(roomId).getType() == ChatRoomType.MATCH;
        List<ChatMemberReadCursor> readCursors = matchRoom
                ? chatRoomMemberRepository.findReadCursorsByRoomId(roomId)
                : List.of();
        long myCursor = findReadCursor(readCursors, userId, true);
        long partnerCursor = findReadCursor(readCursors, userId, false);
        boolean hasNext = messages.size() > size;

        List<ChatMessageResponseDto> responseMessages = messages.stream()
                .limit(size)
                .map(chatMessage -> {
                    Long senderId = chatMessage.getSender().getId();
                    long readerCursor = userId.equals(senderId) ? partnerCursor : myCursor;
                    Boolean isRead = matchRoom ? chatMessage.getId() <= readerCursor : null;
                    return ChatMessageResponseDto.from(chatMessage, hashIdsUtils.encode(senderId), isRead);
                })
                .toList();

        return new ChatMessagesResponseDto(responseMessages, hasNext);
    }

    private long findReadCursor(List<ChatMemberReadCursor> cursors, Long userId, boolean ownCursor) {
        return cursors.stream()
                .filter(cursor -> ownCursor == userId.equals(cursor.userId()))
                .mapToLong(ChatMemberReadCursor::lastReadMessageId)
                .findFirst()
                .orElse(0L);
    }

    @Transactional
    public ChatReadResult markMessagesAsRead(Long roomId, Long userId, long lastReadMessageId) {
        requireActiveUser(userId);
        chatRoomAccessService.requireCurrentMember(roomId, userId);

        if (lastReadMessageId <= 0 || !chatMessageRepository.existsByIdAndRoom_Id(lastReadMessageId, roomId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        int updatedCount = chatRoomMemberRepository.advanceReadCursor(roomId, userId, lastReadMessageId);
        if (updatedCount == 0) {
            return new ChatReadResult(new ChatReadResponseDto(true), null);
        }

        ChatReadEventDto event = ChatReadEventDto.of(
                roomId,
                hashIdsUtils.encode(userId),
                lastReadMessageId
        );

        return new ChatReadResult(new ChatReadResponseDto(true), event);
    }

    @Transactional(readOnly = true)
    public ChatUnreadCountResponseDto getTotalUnreadCount(Long userId) {
        return new ChatUnreadCountResponseDto(getTotalUnreadCountValue(userId));
    }

    @Transactional(readOnly = true)
    public int getTotalUnreadCountValue(Long userId) {
        requireActiveUser(userId);
        return chatMessageRepository.countUnreadByCurrentMemberId(userId);
    }

    @Transactional(readOnly = true)
    public ChatNotificationDto createNotification(
            Long roomId,
            Long senderId,
            Long receiverId,
            ChatMessageResponseDto messageDto
    ) {
        Users sender = getUser(senderId);
        int unreadCount = chatMessageRepository.countUnreadByRoomIds(List.of(roomId), receiverId).stream()
                .findFirst()
                .map(count -> count.unreadCount().intValue())
                .orElse(0);
        int totalUnreadCount = chatMessageRepository.countUnreadByCurrentMemberId(receiverId);

        return new ChatNotificationDto(
                roomId,
                messageDto.getSenderId(),
                sender.getNickname(),
                sender.getProfileImageUrl(),
                messageDto.getMessage(),
                messageDto.getCreatedAt(),
                unreadCount,
                totalUnreadCount
        );
    }

    @Transactional
    public ChatMessageResponseDto sendMessage(Long roomId, Long senderId, String message) {
        validateSendRequest(roomId, senderId, message);
        Users sender = requireActiveUser(senderId);
        // Serialize inserts in the same room so a later ID cannot become visible before an earlier one.
        ChatRoom chatRoom = chatRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        chatRoomAccessService.requireCurrentMember(roomId, senderId);
        validateSendable(chatRoom);

        ChatMessage chatMessage = ChatMessage.create(chatRoom, sender, message.trim());
        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);

        Boolean isRead = chatRoom.getType() == ChatRoomType.MATCH ? Boolean.FALSE : null;
        return ChatMessageResponseDto.from(savedMessage, hashIdsUtils.encode(senderId), isRead);
    }

    @Transactional(readOnly = true)
    public Long getPartnerId(Long roomId, Long userId) {
        chatRoomAccessService.requireCurrentMember(roomId, userId);

        return chatRoomRepository.findPartnerIdByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CHAT_PARTICIPANT));
    }

    @Transactional(readOnly = true)
    public boolean isRecruitmentRoom(Long roomId) {
        return chatRoomRepository.findTypeById(roomId).orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND))
                == ChatRoomType.RECRUITMENT;
    }

    @Transactional
    public ChatRoom createChatRoomIfNotExists(Long matchRequestId) {
        // 방이 아직 없을 때도 매칭 요청 행을 잠가 동시 생성을 직렬화한다.
        MatchRequests matchRequest = matchRepository.findByIdForUpdate(matchRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_REQUEST_NOT_FOUND));
        return chatRoomRepository.findByMatchRequest_Id(matchRequestId)
                .orElseGet(() -> createMatchRoom(matchRequest));
    }

    private ChatRoom createMatchRoom(MatchRequests matchRequest) {
        ChatRoom room = chatRoomRepository.save(ChatRoom.forMatch(matchRequest));
        chatRoomMemberRepository.saveAll(List.of(
                ChatRoomMember.join(room, matchRequest.getUserLow(), room.getCreatedAt()),
                ChatRoomMember.join(room, matchRequest.getUserHigh(), room.getCreatedAt())
        ));
        return room;
    }

    @Transactional
    public void closeMatchRoomByMatchRequestId(Long matchRequestId) {
        chatRoomRepository.findByMatchRequest_Id(matchRequestId)
                .ifPresent(ChatRoom::close);
    }

    @Transactional
    public void closeMatchRoomsByUserId(Long userId) {
        List<ChatRoom> chatRooms = chatRoomRepository.findMatchRoomsByParticipantId(userId);

        chatRooms.forEach(ChatRoom::close);
    }

    @Transactional
    public void closeOtherMatchRoomsByUserId(Long userId, Long excludedMatchRequestId) {
        List<ChatRoom> chatRooms = chatRoomRepository.findMatchRoomsByParticipantId(userId);

        chatRooms.stream()
                .filter(chatRoom -> !chatRoom.getMatchRequest().getId().equals(excludedMatchRequestId))
                .forEach(ChatRoom::close);
    }

    private ChatRoom getChatRoom(Long roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    private Users getUser(Long userId) {
        return usersRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private void validateMessagePageSize(int size) {
        if (size < MIN_MESSAGE_PAGE_SIZE || size > MAX_MESSAGE_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateSendRequest(Long roomId, Long senderId, String message) {
        if (roomId == null || senderId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        if (message == null || message.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.EMPTY_CHAT_MESSAGE);
        }

        if (message.length() > 500) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_TOO_LONG);
        }
    }

    private void validateSendable(ChatRoom chatRoom) {
        if (chatRoom.isClosed() || (chatRoom.getCloseAt() != null
                && !chatRoom.getCloseAt().isAfter(LocalDateTime.now(ZoneId.of("Asia/Seoul"))))) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_CLOSED);
        }
    }

    private Users requireActiveUser(Long userId) {
        Users user = getUser(userId);
        if (user.getStatus() != UserStatus.ACTIVE || user.getRole() != UserRole.USER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return user;
    }
}
