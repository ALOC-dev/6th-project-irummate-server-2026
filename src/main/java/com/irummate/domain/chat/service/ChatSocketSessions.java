package com.irummate.domain.chat.service;

import com.irummate.global.jwt.WebSocketPrincipal;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatSocketSessions {

    public static final String EVENTS_QUEUE = "/queue/chat-events";
    public static final String NOTIFICATIONS_QUEUE = "/queue/chat-notifications";
    static final String ROOM_HEADER = "chatRoomId";
    static final String SOCKET_HEADER = "chatTargetSocket";

    private final Map<String, Socket> sockets = new ConcurrentHashMap<>();

    @EventListener
    public void connected(SessionConnectedEvent event) {
        if (event.getUser() instanceof WebSocketPrincipal principal) {
            String id = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
            if (id != null) sockets.put(id, new Socket(id, principal, ConcurrentHashMap.newKeySet()));
        }
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        sockets.remove(event.getSessionId());
    }

    List<Socket> activeSockets() {
        Instant now = Instant.now();
        return sockets.values().stream().filter(socket -> socket.principal().getExpiresAt().isAfter(now)).toList();
    }

    // Called while the room row is locked, before the removal commits. Only live sockets need a marker.
    void revokeMembership(Long roomId, Long userId) {
        List<Socket> revoked = sockets.values().stream()
                .filter(socket -> userId.equals(socket.principal().getUserId()))
                .filter(socket -> socket.revokedRooms().add(roomId))
                .toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    revoked.forEach(socket -> socket.revokedRooms().remove(roomId));
                }
            }
        });
    }

    public boolean allowsOutbound(Message<?> message) {
        var headers = message.getHeaders();
        String destination = SimpMessageHeaderAccessor.getDestination(headers);
        boolean protectedQueue = destination != null && (destination.startsWith(EVENTS_QUEUE + "-user")
                || destination.startsWith(NOTIFICATIONS_QUEUE + "-user"));
        Object room = headers.get(ROOM_HEADER);
        if (!protectedQueue && room == null) return true;
        String socketId = SimpMessageHeaderAccessor.getSessionId(headers);
        if (!(room instanceof Long roomId) || socketId == null || !socketId.equals(headers.get(SOCKET_HEADER))) return false;
        Socket socket = sockets.get(socketId);
        return socket != null && socket.principal().getExpiresAt().isAfter(Instant.now())
                && !socket.revokedRooms().contains(roomId);
    }

    record Socket(String id, WebSocketPrincipal principal, Set<Long> revokedRooms) {
    }
}
