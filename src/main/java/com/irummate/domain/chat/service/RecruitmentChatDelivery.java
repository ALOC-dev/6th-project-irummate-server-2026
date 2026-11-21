package com.irummate.domain.chat.service;

import com.irummate.domain.auth.repository.LoginSessionRepository;
import com.irummate.domain.chat.dto.ChatMessageEventDto;
import com.irummate.domain.chat.dto.ChatMessageResponseDto;
import com.irummate.domain.chat.dto.ChatNotificationDto;
import com.irummate.domain.chat.dto.ChatReadEventDto;
import com.irummate.domain.chat.entity.ChatRoomType;
import com.irummate.domain.chat.repository.ChatMessageRepository;
import com.irummate.domain.chat.repository.ChatRoomRepository;
import com.irummate.domain.user.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecruitmentChatDelivery {

    private final ChatRoomRepository rooms;
    private final LoginSessionRepository loginSessions;
    private final ChatSocketSessions sockets;
    private final ChatMessageRepository messages;
    private final UsersRepository users;
    private final SimpMessagingTemplate messaging;

    @Transactional
    public void publishMessage(Long roomId, Long senderId, ChatMessageResponseDto message) {
        List<ChatSocketSessions.Socket> recipients = lockRoomAndFindRecipients(roomId);
        ChatMessageEventDto event = ChatMessageEventDto.from(roomId, message);
        for (var socket : recipients) {
            send(socket, roomId, ChatSocketSessions.EVENTS_QUEUE, event);
        }

        var otherSockets = recipients.stream().filter(socket -> !senderId.equals(socket.principal().getUserId())).toList();
        if (otherSockets.isEmpty()) return;

        var sender = users.findById(senderId).orElseThrow();
        List<Long> userIds = otherSockets.stream().map(socket -> socket.principal().getUserId()).distinct().toList();
        var unread = messages.countUnreadByRecipients(roomId, userIds).stream()
                .collect(Collectors.toMap(ChatMessageRepository.RecipientUnreadCount::getUserId, Function.identity()));
        for (var socket : otherSockets) {
            var count = unread.get(socket.principal().getUserId());
            var notification = new ChatNotificationDto(roomId, message.getSenderId(), sender.getNickname(),
                    sender.getProfileImageUrl(), message.getMessage(), message.getCreatedAt(),
                    count == null ? 0 : count.getUnreadCount().intValue(),
                    count == null ? 0 : count.getTotalUnreadCount().intValue());
            send(socket, roomId, ChatSocketSessions.NOTIFICATIONS_QUEUE, notification);
        }
    }

    @Transactional
    public void publishRead(Long roomId, ChatReadEventDto event) {
        for (var socket : lockRoomAndFindRecipients(roomId)) {
            send(socket, roomId, ChatSocketSessions.EVENTS_QUEUE, event);
        }
    }

    private List<ChatSocketSessions.Socket> lockRoomAndFindRecipients(Long roomId) {
        // Membership changes take the same row lock. The outbound guard also checks already queued frames.
        if (rooms.findByIdForUpdate(roomId).filter(room -> room.getType() == ChatRoomType.RECRUITMENT).isEmpty()) {
            return List.of();
        }
        List<ChatSocketSessions.Socket> candidates = sockets.activeSockets();
        if (candidates.isEmpty()) return List.of();
        List<UUID> loginIds = candidates.stream().map(socket -> socket.principal().getSessionId()).distinct().toList();
        var authorized = loginSessions.findActiveChatSessions(roomId, loginIds, LocalDateTime.now(ZoneId.of("Asia/Seoul")))
                .stream().collect(Collectors.toMap(LoginSessionRepository.ChatSession::sessionId,
                        LoginSessionRepository.ChatSession::userId));
        return candidates.stream().filter(socket -> socket.principal().getUserId()
                .equals(authorized.get(socket.principal().getSessionId()))).toList();
    }

    private void send(ChatSocketSessions.Socket socket, Long roomId, String queue, Object payload) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId(socket.id());
        headers.setHeader(ChatSocketSessions.ROOM_HEADER, roomId);
        headers.setHeader(ChatSocketSessions.SOCKET_HEADER, socket.id());
        headers.setLeaveMutable(true);
        // Using the socket ID as the user destination confines this message to that exact socket.
        messaging.convertAndSendToUser(socket.id(), queue, payload, headers.getMessageHeaders());
    }
}
