package com.irummate.domain.chat.service;

import com.irummate.global.jwt.WebSocketPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.messaging.SessionConnectedEvent;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSocketSessionsTest {

    @Test
    void queuedRoomFramesAreDeniedOnEverySocketAndRestoredIfRemovalRollsBack() {
        ChatSocketSessions sockets = new ChatSocketSessions();
        WebSocketPrincipal principal = new WebSocketPrincipal(5L, UUID.randomUUID(), Instant.now().plusSeconds(60));
        sockets.connected(new SessionConnectedEvent(this, frame("phone", 7L), principal));
        sockets.connected(new SessionConnectedEvent(this, frame("web", 7L), principal));
        Message<?> queuedForPhone = frame("phone", 7L);
        Message<?> queuedForWeb = frame("web", 7L);
        assertThat(sockets.allowsOutbound(queuedForPhone)).isTrue();
        assertThat(sockets.allowsOutbound(queuedForWeb)).isTrue();

        TransactionSynchronizationManager.initSynchronization();
        try {
            sockets.revokeMembership(7L, 5L);
            assertThat(sockets.allowsOutbound(queuedForPhone)).isFalse();
            assertThat(sockets.allowsOutbound(queuedForWeb)).isFalse();
            assertThat(sockets.allowsOutbound(frame("web", 8L))).isTrue();
            TransactionSynchronizationManager.getSynchronizations().forEach(
                    sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            assertThat(sockets.allowsOutbound(queuedForPhone)).isTrue();
            assertThat(sockets.allowsOutbound(queuedForWeb)).isTrue();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Message<byte[]> frame(String socketId, Long roomId) {
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId(socketId);
        headers.setDestination(ChatSocketSessions.EVENTS_QUEUE + "-user" + socketId);
        headers.setHeader(ChatSocketSessions.ROOM_HEADER, roomId);
        headers.setHeader(ChatSocketSessions.SOCKET_HEADER, socketId);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
