package com.irummate.domain.chat.service;

import com.irummate.domain.chat.dto.ChatMessageResponseDto;
import com.irummate.domain.chat.dto.ChatMessagesResponseDto;
import com.irummate.domain.chat.dto.ChatNotificationDto;
import com.irummate.domain.chat.dto.ChatReadResponseDto;
import com.irummate.domain.chat.dto.ChatRoomLastMessageDto;
import com.irummate.domain.chat.dto.ChatRoomPartnerDto;
import com.irummate.domain.chat.dto.ChatRoomResponseDto;
import com.irummate.domain.chat.dto.ChatRoomsResponseDto;
import com.irummate.domain.chat.dto.ChatRoomUnreadCountDto;
import com.irummate.domain.chat.dto.ChatUnreadCountResponseDto;
import com.irummate.domain.chat.entity.ChatMessage;
import com.irummate.domain.chat.entity.ChatRoom;
import com.irummate.domain.chat.entity.ChatRoomStatus;
import com.irummate.domain.chat.repository.ChatMessageRepository;
import com.irummate.domain.chat.repository.ChatRoomRepository;
import com.irummate.domain.matching.entity.MatchStatus;
import com.irummate.domain.user.entity.UserStatus;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.util.HashIdsUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.irummate.domain.matching.util.MatchingDtoMapper.toCardStatus;

@Service
public class ChatService {

    private static final int MIN_MESSAGE_PAGE_SIZE = 1;
    private static final int MAX_MESSAGE_PAGE_SIZE = 100;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UsersRepository usersRepository;
    private final HashIdsUtils hashIdsUtils;

    public ChatService(ChatRoomRepository chatRoomRepository,
                       ChatMessageRepository chatMessageRepository,
                       UsersRepository usersRepository,
                       HashIdsUtils hashIdsUtils) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.usersRepository = usersRepository;
        this.hashIdsUtils = hashIdsUtils;
    }

    @Transactional(readOnly = true)
    public ChatRoomsResponseDto getChatRooms(Long userId) {
        List<ChatRoomPartnerDto> roomPartners = chatRoomRepository.findRoomPartnersByUserId(userId);

        List<Long> roomIds = roomPartners.stream()
                .map(ChatRoomPartnerDto::roomId)
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

        List<ChatRoomResponseDto> rooms = roomPartners.stream()
                .map(room -> {
                    ChatRoomLastMessageDto lastMessage = lastMessageMap.get(room.roomId());
                    Long unreadCount = unreadCountMap.getOrDefault(room.roomId(), 0L);

                    return new ChatRoomResponseDto(
                            room.roomId(),
                            hashIdsUtils.encode(room.partnerId()),
                            room.partnerName(),
                            room.partnerProfileImageUrl(),
                            lastMessage == null ? null : lastMessage.lastMessage(),
                            lastMessage == null ? null : lastMessage.lastMessageTime(),
                            unreadCount.intValue(),
                            room.status(),
                            toCardStatus(room.myMatchStatus(), room.partnerMatchStatus()),
                            isConfirmedByMe(room.myMatchStatus()),
                            canConfirm(room.myMatchStatus(), room.partnerMatchStatus())
                    );
                })
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
        validateRoomExists(roomId);
        validateRoomParticipant(roomId, userId);
        validateMessagePageSize(size);

        // size + 1개를 조회해서 다음 페이지가 있는지 판단한다.
        PageRequest pageRequest = PageRequest.of(0, size + 1);

        List<ChatMessage> messages = cursor == null
                ? chatMessageRepository.findByRoomIdOrderByIdDesc(roomId, pageRequest)
                : chatMessageRepository.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, cursor, pageRequest);

        boolean hasNext = messages.size() > size;

        List<ChatMessageResponseDto> responseMessages = messages.stream()
                .limit(size)
                .map(chatMessage -> ChatMessageResponseDto.from(
                        chatMessage,
                        hashIdsUtils.encode(chatMessage.getSenderId())
                ))
                .toList();

        return new ChatMessagesResponseDto(responseMessages, hasNext);
    }

    @Transactional
    public ChatReadResponseDto markMessagesAsRead(Long roomId, Long userId) {
        validateRoomExists(roomId);
        validateRoomParticipant(roomId, userId);

        // 내가 보낸 메시지는 읽음 처리 대상에서 제외한다.
        List<ChatMessage> unreadMessages =
                chatMessageRepository.findByRoomIdAndSenderIdNotAndIsReadFalse(roomId, userId);

        unreadMessages.forEach(ChatMessage::markAsRead);

        return new ChatReadResponseDto(true);
    }

    @Transactional(readOnly = true)
    public ChatUnreadCountResponseDto getTotalUnreadCount(Long userId) {
        return new ChatUnreadCountResponseDto(getTotalUnreadCountValue(userId));
    }

    @Transactional(readOnly = true)
    public int getTotalUnreadCountValue(Long userId) {
        return chatMessageRepository.countUnreadByParticipantId(userId);
    }

    @Transactional(readOnly = true)
    public ChatNotificationDto createNotification(
            Long roomId,
            Long senderId,
            Long receiverId,
            ChatMessageResponseDto messageDto
    ) {
        Users sender = getUser(senderId);
        int unreadCount = chatMessageRepository.countByRoomIdAndSenderIdNotAndIsReadFalse(roomId, receiverId);
        int totalUnreadCount = getTotalUnreadCountValue(receiverId);

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
        ChatRoom chatRoom = getChatRoom(roomId);
        validateCertifiedUser(senderId);
        validateSendable(chatRoom, senderId, message);
        validateRoomParticipant(roomId, senderId);

        ChatMessage chatMessage = ChatMessage.create(roomId, senderId, message.trim());
        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);

        return ChatMessageResponseDto.from(savedMessage, hashIdsUtils.encode(senderId));
    }

    @Transactional(readOnly = true)
    public Long getPartnerId(Long roomId, Long userId) {
        validateRoomExists(roomId);

        return chatRoomRepository.findPartnerIdByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CHAT_PARTICIPANT));
    }

    @Transactional
    public ChatRoom createChatRoomIfNotExists(Long matchRequestId) {
        // 상호 HEART 상태가 되었을 때 matchRequestId 기준으로 채팅방을 한 번만 생성한다.
        return chatRoomRepository.findByMatchRequestId(matchRequestId)
                .orElseGet(() -> saveChatRoomOrFindExisting(matchRequestId));
    }

    @Transactional
    public void closeChatRoomByMatchRequestId(Long matchRequestId) {
        // 최종 매칭이 확정되면 기존 대화는 조회만 가능하도록 채팅방을 닫는다.
        chatRoomRepository.findByMatchRequestId(matchRequestId)
                .ifPresent(ChatRoom::close);
    }

    @Transactional
    public void closeChatRoomsByUserId(Long userId) {
        List<ChatRoom> chatRooms = chatRoomRepository.findAllByParticipantId(userId);

        chatRooms.forEach(ChatRoom::close);
    }

    @Transactional
    public void closeChatRoomsByUserIdExceptMatchRequestId(Long userId, Long excludedMatchRequestId) {
        List<ChatRoom> chatRooms = chatRoomRepository.findAllByParticipantId(userId);

        chatRooms.stream()
                .filter(chatRoom -> !chatRoom.getMatchRequestId().equals(excludedMatchRequestId))
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

    private void validateRoomExists(Long roomId) {
        if (!chatRoomRepository.existsById(roomId)) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND);
        }
    }

    private void validateRoomParticipant(Long roomId, Long userId) {
        if (userId == null || !chatRoomRepository.existsByRoomIdAndParticipantId(roomId, userId)) {
            throw new BusinessException(ErrorCode.NOT_CHAT_PARTICIPANT);
        }
    }

    private void validateMessagePageSize(int size) {
        if (size < MIN_MESSAGE_PAGE_SIZE || size > MAX_MESSAGE_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateSendable(ChatRoom chatRoom, Long senderId, String message) {
        if (chatRoom.isClosed()) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_CLOSED);
        }

        if (senderId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        if (message == null || message.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.EMPTY_CHAT_MESSAGE);
        }

        if (message.length() > 500) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_TOO_LONG);
        }
    }

    private ChatRoom saveChatRoomOrFindExisting(Long matchRequestId) {
        try {
            return chatRoomRepository.saveAndFlush(
                    ChatRoom.builder()
                            .matchRequestId(matchRequestId)
                            .status(ChatRoomStatus.OPEN)
                            .build()
            );
        } catch (DataIntegrityViolationException e) {
            return chatRoomRepository.findByMatchRequestId(matchRequestId)
                    .orElseThrow(() -> e);
        }
    }

    private void validateCertifiedUser(Long userId) {
        Users user = getUser(userId);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
