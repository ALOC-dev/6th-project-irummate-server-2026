package com.irummate;

import com.irummate.domain.chat.entity.ChatRoomType;
import com.irummate.domain.recruitment.dto.RecruitmentCreateRequestDto;
import com.irummate.domain.recruitment.dto.RecruitmentParticipantRemoveRequestDto;
import com.irummate.domain.recruitment.entity.RecruitmentCategory;
import com.irummate.domain.recruitment.service.RecruitmentParticipationService;
import com.irummate.domain.recruitment.service.RecruitmentService;
import com.irummate.global.security.AccessTokenAuthenticationService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class StompSecurityIntegrationTest extends SecurityIntegrationSupport {
    @Autowired SimpUserRegistry users;
    @Autowired AccessTokenAuthenticationService authentication;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired RecruitmentService recruitmentService;
    @Autowired RecruitmentParticipationService recruitmentParticipation;
    private final List<WebSocketStompClient> clients = new ArrayList<>();
    private final List<Connection> connections = new ArrayList<>();

    @Test
    void webSocketAuthenticationLoadsSessionAndAccountInOneSelect() {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            assertThat(authentication.authenticate(access(ALICE, aliceSession)).userId()).isEqualTo(ALICE);
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    @AfterEach
    void closeSockets() {
        try {
            for (Connection connection : connections) {
                if (connection.session == null || !connection.session.isConnected()) {
                    continue;
                }
                try {
                    connection.session.disconnect();
                } catch (MessageDeliveryException exception) {
                    // The broker can close the socket between the connection check and DISCONNECT.
                    if (!(exception.getCause() instanceof IllegalStateException cause)
                            || !"Message will not be sent because the WebSocket session has been closed".equals(cause.getMessage())) {
                        throw exception;
                    }
                }
            }
        } finally {
            clients.forEach(WebSocketStompClient::stop);
        }
    }

    @Test
    void brokerSendForgeryIsRejectedAndExistingSubscriberReceivesNothing() throws Exception {
        var receiver = connect(access(BOB, bobSession));
        String destination = "/queue/user/" + hashIds.encode(BOB);
        BlockingQueue<String> received = subscribe(receiver, BOB, "/user/queue/chat-notifications");
        var attacker = connect(access(ALICE, aliceSession));

        attacker.session.send(destination, "forged-notification".getBytes(StandardCharsets.UTF_8));

        String error = attacker.errors.poll(3, TimeUnit.SECONDS);
        assertThat(received.poll(300, TimeUnit.MILLISECONDS)).as("subscriber must not receive forged data").isNull();
        assertThat(error).contains("INVALID_WEBSOCKET_DESTINATION");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_messages", Long.class)).isZero();
    }

    @Test
    void rawServerMessageFrameCannotBypassClientSendRestrictions() throws Exception {
        var receiver = connect(access(BOB, bobSession));
        String destination = "/queue/user/" + hashIds.encode(BOB);
        var received = subscribe(receiver, BOB, "/user/queue/chat-notifications");
        BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        var rawClient = new StandardWebSocketClient();
        var raw = rawClient.execute(new TextWebSocketHandler() {
            @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                frames.add(message.getPayload());
            }
        }, new WebSocketHttpHeaders(), URI.create("ws://localhost:" + port + "/ws/chat/websocket"))
                .get(5, TimeUnit.SECONDS);
        try {
            raw.sendMessage(new TextMessage("STOMP\naccept-version:1.2\nhost:localhost\nAuthorization:Bearer "
                    + access(ALICE, aliceSession) + "\n\n\0"));
            assertThat(frames.poll(5, TimeUnit.SECONDS)).startsWith("CONNECTED");
            raw.sendMessage(new TextMessage("MESSAGE\ndestination:" + destination
                    + "\nsubscription:forged\nmessage-id:forged\n\nforged-notification\0"));
            assertThat(frames.poll(5, TimeUnit.SECONDS)).contains("INVALID_WEBSOCKET_DESTINATION");
            assertThat(received.poll(300, TimeUnit.MILLISECONDS)).isNull();
        } finally {
            if (raw.isOpen()) raw.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/room/1", "/user/queue/chat-errors", "/app/chat/send/extra"})
    void otherBrokerAndSimilarApplicationDestinationsAreRejectedOverRealConnection(String destination) throws Exception {
        var attacker = connect(access(ALICE, aliceSession));
        attacker.session.send(destination, "forged".getBytes(StandardCharsets.UTF_8));
        assertThat(attacker.errors.poll(5, TimeUnit.SECONDS)).contains("INVALID_WEBSOCKET_DESTINATION");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_messages", Long.class)).isZero();
    }

    @Test
    void normalApplicationSendStillDeliversRoomMessageAndPersonalNotification() throws Exception {
        var receiver = connect(access(BOB, bobSession));
        var roomMessages = subscribe(receiver, BOB, "/user/queue/chat-events");
        var notifications = subscribe(receiver, BOB, "/user/queue/chat-notifications");
        var sender = connect(access(ALICE, aliceSession));
        var senderEvents = subscribe(sender, ALICE, "/user/queue/chat-events");
        var senderNotifications = subscribe(sender, ALICE, "/user/queue/chat-notifications");
        var outsider = connect(access(OUTSIDER, outsiderSession));
        var outsiderEvents = subscribe(outsider, OUTSIDER, "/user/queue/chat-events");

        sendChat(sender, "hello");

        String roomMessage = roomMessages.poll(5, TimeUnit.SECONDS);
        String notification = notifications.poll(5, TimeUnit.SECONDS);
        assertThat(roomMessage).isNotNull();
        assertThat(notification).isNotNull();
        var event = json.readTree(roomMessage);
        assertThat(event.path("message").asString()).isEqualTo("hello");
        assertThat(event.path("senderId").asString()).isEqualTo(hashIds.encode(ALICE));
        assertThat(event.path("isRead").isBoolean()).isTrue();
        assertThat(event.path("isRead").asBoolean()).isFalse();
        assertThat(json.readTree(notification).path("message").asString()).isEqualTo("hello");
        assertThat(senderEvents.poll(5, TimeUnit.SECONDS)).contains("hello");
        assertThat(readChat(roomId, event.path("messageId").asLong(), access(BOB, bobSession))).isEqualTo(200);
        assertThat(senderEvents.poll(5, TimeUnit.SECONDS)).contains("\"type\":\"READ\"");
        assertThat(roomMessages.poll(5, TimeUnit.SECONDS)).contains("\"type\":\"READ\"");
        assertThat(outsiderEvents.poll(300, TimeUnit.MILLISECONDS)).isNull();
        assertThat(senderNotifications).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_messages", Long.class)).isEqualTo(1);
    }

    @Test
    void removedRecruitmentMemberStopsReceivingEventsOnBothOpenSockets() throws Exception {
        long recruitmentId = recruitmentService.createRecruitment(ALICE, RecruitmentCreateRequestDto.builder()
                .category(RecruitmentCategory.MEAL).title("Group chat test").maxParticipants(2)
                .activityAt(LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(1)).build()).getRecruitmentId();
        long groupRoom = chatService.getChatRooms(ALICE).getRooms().stream()
                .filter(room -> room.getType() == ChatRoomType.RECRUITMENT).findFirst().orElseThrow().getRoomId();
        chatService.sendMessage(groupRoom, ALICE, "history before join");
        recruitmentParticipation.joinRecruitment(BOB, recruitmentId);
        recruitmentParticipation.joinRecruitment(OUTSIDER, recruitmentId);
        assertThat(chatService.getTotalUnreadCountValue(BOB)).isZero();
        assertThat(chatService.getMessages(groupRoom, BOB, null, 10).getMessages()).hasSize(1);

        var host = connect(access(ALICE, aliceSession));
        var hostOtherDevice = connect(access(ALICE, aliceSession));
        var removed = connect(access(BOB, bobSession));
        var removedOtherDevice = connect(access(BOB, bobSession));
        String events = "/user/queue/chat-events";
        var hostEvents = subscribe(hostOtherDevice, ALICE, events);
        var removedEvents = subscribe(removed, BOB, events);
        var removedOtherEvents = subscribe(removedOtherDevice, BOB, events);
        var removedNotifications = subscribe(removed, BOB, "/user/queue/chat-notifications");
        var removedOtherNotifications = subscribe(removedOtherDevice, BOB, "/user/queue/chat-notifications");
        var chatErrors = subscribe(removed, BOB, "/user/queue/chat-errors");
        host.session.send(chatHeaders(), ("{\"roomId\":" + groupRoom + ",\"message\":\"before removal\"}")
                .getBytes(StandardCharsets.UTF_8));
        assertThat(hostEvents.poll(5, TimeUnit.SECONDS)).contains("before removal");
        assertThat(removedEvents.poll(5, TimeUnit.SECONDS)).contains("before removal");
        assertThat(removedOtherEvents.poll(5, TimeUnit.SECONDS)).contains("before removal");
        assertThat(removedNotifications.poll(5, TimeUnit.SECONDS)).contains("before removal", "\"unreadCount\":1");
        assertThat(removedOtherNotifications.poll(5, TimeUnit.SECONDS)).contains("before removal");

        recruitmentParticipation.removeParticipant(ALICE, recruitmentId, RecruitmentParticipantRemoveRequestDto.builder()
                .targetUserId(hashIds.encode(BOB)).removalReason("Group test removal").build());

        host.session.send(chatHeaders(), ("{\"roomId\":" + groupRoom + ",\"message\":\"after removal\"}")
                .getBytes(StandardCharsets.UTF_8));
        String after = hostEvents.poll(5, TimeUnit.SECONDS);
        assertThat(after).contains("after removal");
        long messageId = json.readTree(after).path("messageId").asLong();
        assertThat(readChat(groupRoom, messageId, access(OUTSIDER, outsiderSession))).isEqualTo(200);
        assertThat(hostEvents.poll(5, TimeUnit.SECONDS)).contains("\"type\":\"READ\"");

        assertThat(request("GET", "/api/chat/rooms/" + groupRoom + "/messages", access(BOB, bobSession), null).statusCode())
                .isEqualTo(403);
        removed.session.send(chatHeaders(), ("{\"roomId\":" + groupRoom + ",\"message\":\"removed sender\"}")
                .getBytes(StandardCharsets.UTF_8));
        assertThat(chatErrors.poll(5, TimeUnit.SECONDS)).contains("NOT_CHAT_PARTICIPANT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_messages WHERE room_id = ?", Long.class, groupRoom))
                .isEqualTo(3);
        assertThat(removedEvents.poll(300, TimeUnit.MILLISECONDS)).isNull();
        assertThat(removedOtherEvents).isEmpty();
        assertThat(removedNotifications).isEmpty();
        assertThat(removedOtherNotifications).isEmpty();
        sendChat(host, "other room still works");
        assertThat(removedEvents.poll(5, TimeUnit.SECONDS)).contains("other room still works", "\"roomId\":" + roomId);
        assertThat(removedOtherEvents.poll(5, TimeUnit.SECONDS)).contains("other room still works");
    }

    @Test
    void refreshedAccessTokenCanOpenWebSocketWithoutRevokingExistingSession() throws Exception {
        var existing = connect(access(ALICE, aliceSession));
        var refreshed = request("POST", "/api/auth/refresh", null, "refreshToken=alice-refresh");
        assertThat(refreshed.statusCode()).isEqualTo(200);
        String token = json.readTree(refreshed.body()).path("data").path("accessToken").asString();

        var renewed = connect(token);

        assertThat(renewed.session.isConnected()).isTrue();
        assertThat(existing.session.isConnected()).isTrue();
        assertThat(authentication.authenticate(token).sessionId()).isEqualTo(aliceSession);
    }

    @Test
    void nonParticipantCannotSendToRoomThroughApplication() throws Exception {
        var outsider = connect(access(OUTSIDER, outsiderSession));
        var errors = subscribe(outsider, OUTSIDER, "/user/queue/chat-errors");
        sendChat(outsider, "not-allowed");
        assertThat(errors.poll(5, TimeUnit.SECONDS)).contains("NOT_CHAT_PARTICIPANT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_messages", Long.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-session", "revoked", "other-owner", "malformed-uuid", "short-uuid",
            "session-expired", "jwt-expired", "bad-signature", "wrong-type", "invalid-subject", "missing-sid"})
    void invalidSessionCannotConnect(String scenario) throws Exception {
        String sid = switch (scenario) {
            case "missing-session" -> UUID.randomUUID().toString();
            case "other-owner" -> bobSession.toString();
            case "malformed-uuid" -> "invalid-uuid";
            case "short-uuid" -> "1-1-1-1-1";
            case "missing-sid" -> null;
            default -> aliceSession.toString();
        };
        if (scenario.equals("revoked")) jdbc.update("UPDATE login_sessions SET revoked_at=now() WHERE id=?", aliceSession);
        if (scenario.equals("session-expired")) jdbc.update("UPDATE login_sessions SET expires_at=? WHERE id=?",
                LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(1), aliceSession);
        String token = customToken(scenario.equals("invalid-subject") ? "invalid-id" : hashIds.encode(ALICE),
                sid, scenario.equals("wrong-type") ? "refresh" : "access",
                Instant.now().plusSeconds(scenario.equals("jwt-expired") ? -60 : 900),
                scenario.equals("bad-signature") ? "different-test-secret-with-at-least-32-bytes" : SECRET);
        assertThatThrownBy(() -> connect(token)).isInstanceOf(ExecutionException.class);
        assertThat(connections.getLast().errors.poll(3, TimeUnit.SECONDS)).contains("WEBSOCKET_UNAUTHORIZED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"logout-send", "logout-subscribe", "jwt-expired", "session-expired", "banned", "withdrawn", "owner-changed"})
    void existingConnectionRevalidatesSessionAndExpiry(String change) throws Exception {
        Instant expiry = Instant.now().plusSeconds(change.equals("jwt-expired") ? 4 : 900);
        var connection = connect(customToken(hashIds.encode(ALICE), aliceSession.toString(), "access", expiry, SECRET));
        var receiver = connect(access(BOB, bobSession));
        var messages = subscribe(receiver, BOB, "/user/queue/chat-events");
        switch (change) {
            case "logout-send", "logout-subscribe" -> assertThat(
                    request("POST", "/api/auth/logout", null, "refreshToken=alice-refresh").statusCode()).isEqualTo(200);
            case "jwt-expired" -> await().atMost(Duration.ofSeconds(6)).until(() -> Instant.now().isAfter(expiry));
            case "session-expired" -> jdbc.update("UPDATE login_sessions SET expires_at=? WHERE id=?",
                    LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(1), aliceSession);
            case "banned" -> jdbc.update("UPDATE users SET status='BANNED' WHERE id=?", ALICE);
            case "withdrawn" -> jdbc.update("UPDATE users SET status='WITHDRAWN' WHERE id=?", ALICE);
            case "owner-changed" -> jdbc.update("UPDATE login_sessions SET user_id=? WHERE id=?", BOB, aliceSession);
            default -> throw new IllegalArgumentException(change);
        }
        if (change.equals("logout-subscribe")) {
            connection.session.subscribe("/user/queue/chat-events", discardingHandler());
        } else {
            // A later Authorization header cannot replace CONNECT's authenticated identity/session.
            var headers = chatHeaders();
            headers.add("Authorization", "Bearer " + access(BOB, bobSession));
            headers.add("user", Long.toString(BOB));
            connection.session.send(headers, chatPayload("not-allowed"));
        }
        assertThat(connection.errors.poll(5, TimeUnit.SECONDS)).contains("WEBSOCKET_UNAUTHORIZED");
        assertThat(messages.poll(200, TimeUnit.MILLISECONDS)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_messages", Long.class)).isZero();
    }

    @Test
    void revokedConnectionCanStillDisconnectNormally() throws Exception {
        var connection = connect(access(ALICE, aliceSession));
        assertThat(request("POST", "/api/auth/logout", null, "refreshToken=alice-refresh").statusCode()).isEqualTo(200);
        connection.session.disconnect();
        await().atMost(Duration.ofSeconds(5)).until(() -> users.getUser(Long.toString(ALICE)) == null);
        assertThat(connection.errors).isEmpty();
    }

    @Test
    void legacyPersonalSubscriptionIsRejected() throws Exception {
        var connection = connect(access(ALICE, aliceSession));
        connection.session.subscribe("/queue/user/" + hashIds.encode(BOB), discardingHandler());
        assertThat(connection.errors.poll(5, TimeUnit.SECONDS)).contains("INVALID_WEBSOCKET_DESTINATION");
    }

    private int readChat(long targetRoomId, long messageId, String token) throws Exception {
        HttpRequest read = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/chat/rooms/" + targetRoomId + "/read"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"lastReadMessageId\":" + messageId + "}"))
                .timeout(Duration.ofSeconds(5)).build();
        return http.send(read, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private void sendChat(Connection connection, String message) {
        connection.session.send(chatHeaders(), chatPayload(message));
    }

    private StompHeaders chatHeaders() {
        var headers = new StompHeaders();
        headers.setDestination("/app/chat/send");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private byte[] chatPayload(String message) {
        return json.writeValueAsBytes(java.util.Map.of("roomId", roomId, "message", message));
    }

    private StompFrameHandler discardingHandler() {
        return new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return byte[].class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {}
        };
    }

    private Connection connect(String token) throws Exception {
        var client = new WebSocketStompClient(new StandardWebSocketClient());
        client.start();
        clients.add(client);
        var connection = new Connection();
        connections.add(connection);
        var headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token);
        connection.session = client.connectAsync("ws://localhost:" + port + "/ws/chat/websocket", new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() {
                    @Override public Type getPayloadType(StompHeaders headers) { return byte[].class; }
                    @Override public void handleFrame(StompHeaders headers, Object payload) {
                        connection.errors.add(new String((byte[]) payload, StandardCharsets.UTF_8));
                    }
                }).get(5, TimeUnit.SECONDS);
        return connection;
    }

    private BlockingQueue<String> subscribe(Connection connection, long userId, String destination) {
        long existingSubscriptions = countSubscriptions(userId, destination);
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        connection.session.subscribe(destination, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return byte[].class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                messages.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });
        await().atMost(Duration.ofSeconds(5)).until(() -> countSubscriptions(userId, destination) > existingSubscriptions);
        return messages;
    }

    private long countSubscriptions(long userId, String destination) {
        var user = users.getUser(Long.toString(userId));
        return user == null ? 0 : user.getSessions().stream().flatMap(session -> session.getSubscriptions().stream())
                .filter(subscription -> destination.equals(subscription.getDestination())).count();
    }

    private static class Connection {
        StompSession session;
        final BlockingQueue<String> errors = new LinkedBlockingQueue<>();
    }
}
