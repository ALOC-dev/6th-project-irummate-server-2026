package com.irummate.chat;

import com.irummate.domain.chat.dto.ChatReadResult;
import com.irummate.domain.chat.dto.ChatRoomLastMessageDto;
import com.irummate.domain.chat.dto.ChatRoomPartnerDto;
import com.irummate.domain.chat.dto.ChatRoomResponseDto;
import com.irummate.domain.chat.entity.ChatRoomStatus;
import com.irummate.domain.chat.entity.ChatRoom;
import com.irummate.domain.chat.repository.ChatMessageRepository;
import com.irummate.domain.chat.repository.ChatRoomRepository;
import com.irummate.domain.chat.service.ChatService;
import com.irummate.domain.matching.entity.MatchStatus;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.domain.user.entity.UserStatus;
import com.irummate.domain.user.entity.Users;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.util.HashIdsUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private UsersRepository usersRepository;

    @Mock
    private HashIdsUtils hashIdsUtils;

    @InjectMocks
    private ChatService chatService;

    @Test
    void getChatRoomsSortsByLastMessageTimeAndPlacesEmptyRoomsLast() {
        Long userId = 1L;
        List<ChatRoomPartnerDto> partners = List.of(
                partner(1L, 11L),
                partner(4L, 14L),
                partner(2L, 12L),
                partner(3L, 13L)
        );
        LocalDateTime older = LocalDateTime.of(2026, 9, 24, 10, 0);
        LocalDateTime newer = older.plusMinutes(10);

        when(chatRoomRepository.findRoomPartnersByUserId(userId)).thenReturn(partners);
        when(chatMessageRepository.findLastMessagesByRoomIds(List.of(1L, 4L, 2L, 3L)))
                .thenReturn(List.of(
                        new ChatRoomLastMessageDto(1L, "old", older),
                        new ChatRoomLastMessageDto(2L, "new", newer)
                ));
        when(chatMessageRepository.countUnreadByRoomIds(List.of(1L, 4L, 2L, 3L), userId))
                .thenReturn(List.of());
        when(hashIdsUtils.encode(11L)).thenReturn("user11");
        when(hashIdsUtils.encode(12L)).thenReturn("user12");
        when(hashIdsUtils.encode(13L)).thenReturn("user13");
        when(hashIdsUtils.encode(14L)).thenReturn("user14");

        List<ChatRoomResponseDto> rooms = chatService.getChatRooms(userId).getRooms();

        assertThat(rooms)
                .extracting(ChatRoomResponseDto::getRoomId)
                .containsExactly(2L, 1L, 4L, 3L);
    }

    @Test
    void markMessagesAsReadBulkUpdatesSnapshotAndBuildsReadEvent() {
        Long roomId = 7L;
        Long userId = 2L;

        when(chatRoomRepository.existsById(roomId)).thenReturn(true);
        when(chatRoomRepository.existsByRoomIdAndParticipantId(roomId, userId)).thenReturn(true);
        when(chatMessageRepository.findLastUnreadMessageId(roomId, userId)).thenReturn(Optional.of(105L));
        when(chatMessageRepository.markUnreadMessagesAsRead(roomId, userId, 105L)).thenReturn(3);
        when(hashIdsUtils.encode(userId)).thenReturn("hashed-reader");

        ChatReadResult result = chatService.markMessagesAsRead(roomId, userId);

        assertThat(result.response().getSuccess()).isTrue();
        assertThat(result.event()).isNotNull();
        assertThat(result.event().getType().name()).isEqualTo("READ");
        assertThat(result.event().getRoomId()).isEqualTo(roomId);
        assertThat(result.event().getReaderId()).isEqualTo("hashed-reader");
        assertThat(result.event().getLastReadMessageId()).isEqualTo(105L);
        verify(chatMessageRepository).markUnreadMessagesAsRead(roomId, userId, 105L);
    }

    @Test
    void markMessagesAsReadDoesNotUpdateOrBuildEventWhenNothingIsUnread() {
        Long roomId = 7L;
        Long userId = 2L;

        when(chatRoomRepository.existsById(roomId)).thenReturn(true);
        when(chatRoomRepository.existsByRoomIdAndParticipantId(roomId, userId)).thenReturn(true);
        when(chatMessageRepository.findLastUnreadMessageId(roomId, userId)).thenReturn(Optional.empty());

        ChatReadResult result = chatService.markMessagesAsRead(roomId, userId);

        assertThat(result.response().getSuccess()).isTrue();
        assertThat(result.event()).isNull();
        verify(chatMessageRepository, never()).markUnreadMessagesAsRead(anyLong(), anyLong(), anyLong());
    }

    @Test
    void sendMessageRejectsClosedRoomWithChatRoomClosedError() {
        ChatRoom room = ChatRoom.builder()
                .matchRequestId(10L)
                .status(ChatRoomStatus.CLOSED)
                .build();
        Users sender = org.mockito.Mockito.mock(Users.class);

        when(chatRoomRepository.findById(7L)).thenReturn(Optional.of(room));
        when(usersRepository.findById(2L)).thenReturn(Optional.of(sender));
        when(sender.getStatus()).thenReturn(UserStatus.ACTIVE);

        assertThatThrownBy(() -> chatService.sendMessage(7L, 2L, "hello"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CHAT_ROOM_CLOSED));
    }

    private ChatRoomPartnerDto partner(Long roomId, Long partnerId) {
        return new ChatRoomPartnerDto(
                roomId,
                partnerId,
                "partner-" + partnerId,
                null,
                ChatRoomStatus.OPEN,
                MatchStatus.HEART,
                MatchStatus.HEART
        );
    }
}
