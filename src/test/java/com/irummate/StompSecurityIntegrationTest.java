package com.irummate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class StompSecurityIntegrationTest extends SecurityIntegrationSupport {
    @Autowired SimpUserRegistry users;
    private final List<WebSocketStompClient> clients = new ArrayList<>();

    @AfterEach
    void closeSockets() {
        clients.forEach(WebSocketStompClient::stop);
    }

    @Test
    void brokerSendForgeryIsRejectedAndExistingSubscriberReceivesNothing() throws Exception {
        var receiver = connect(access(BOB, bobSession));
        String destination = "/queue/user/" + hashIds.encode(BOB);
        BlockingQueue<String> received = subscribe(receiver, BOB, destination);
        var attacker = connect(access(ALICE, aliceSession));

        attacker.session.send(destination, "forged-notification".getBytes(StandardCharsets.UTF_8));

        String error = attacker.errors.poll(3, TimeUnit.SECONDS);
        assertThat(received.poll(300, TimeUnit.MILLISECONDS)).as("subscriber must not receive forged data").isNull();
        assertThat(error).contains("INVALID_WEBSOCKET_DESTINATION");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_messages", Long.class)).isZero();
    }

    private Connection connect(String token) throws Exception {
        var client = new WebSocketStompClient(new StandardWebSocketClient());
        client.start();
        clients.add(client);
        var connection = new Connection();
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
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        connection.session.subscribe(destination, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return byte[].class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                messages.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });
        await().atMost(Duration.ofSeconds(5)).until(() -> {
            var user = users.getUser(Long.toString(userId));
            return user != null && user.getSessions().stream().flatMap(s -> s.getSubscriptions().stream())
                    .anyMatch(s -> destination.equals(s.getDestination()));
        });
        return messages;
    }

    private static class Connection {
        StompSession session;
        final BlockingQueue<String> errors = new LinkedBlockingQueue<>();
    }
}
