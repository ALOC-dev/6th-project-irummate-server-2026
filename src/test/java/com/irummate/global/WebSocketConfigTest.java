package com.irummate.global;

import com.irummate.domain.chat.repository.ChatRoomRepository;
import com.irummate.domain.user.entity.UserRole;
import com.irummate.global.security.AccessTokenAuthenticationService;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.config.WebSocketConfig;
import com.irummate.global.jwt.WebSocketPrincipal;
import com.irummate.global.util.HashIdsUtils;
import com.irummate.global.websocket.WebSocketStompErrorHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketConfigTest {

    private final AccessTokenAuthenticationService authenticationService = mock(AccessTokenAuthenticationService.class);
    private final ChatRoomRepository chatRoomRepository = mock(ChatRoomRepository.class);
    private final HashIdsUtils hashIdsUtils = mock(HashIdsUtils.class);
    private final WebSocketConfig config = new WebSocketConfig(
            authenticationService,
            chatRoomRepository,
            hashIdsUtils,
            mock(WebSocketStompErrorHandler.class)
    );
    private final MessageChannel channel = mock(MessageChannel.class);

    @Test
    void allowsOnlyExactChatSendDestination() {
        givenActiveUser(2L);
        Message<byte[]> message = stompMessage(StompCommand.SEND, "/app/chat/send", principal(2L));
        assertThat(inboundInterceptor().preSend(message, channel)).isSameAs(message);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "/topic/room/7", "/queue/user/other", "/user/queue/chat-errors",
            "/app/unknown", "/app/chat/send/extra", "/app/chat/sender", "/app/chat/send?x=1"})
    void rejectsEveryOtherSendDestination(String destination) {
        givenActiveUser(2L);
        Message<byte[]> message = stompMessage(StompCommand.SEND, destination, principal(2L));
        assertThatThrownBy(() -> inboundInterceptor().preSend(message, channel))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_WEBSOCKET_DESTINATION));
    }

    @Test
    void rejectsUnauthenticatedSend() {
        Message<byte[]> message = stompMessage(StompCommand.SEND, "/app/chat/send", null);
        assertThatThrownBy(() -> inboundInterceptor().preSend(message, channel))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WEBSOCKET_UNAUTHORIZED));
    }

    @Test
    void rejectsServerMessageCommandFromClientEvenWithAllowedDestination() {
        givenActiveUser(2L);
        Message<byte[]> message = stompMessage(StompCommand.MESSAGE, "/app/chat/send", principal(2L));
        assertThatThrownBy(() -> inboundInterceptor().preSend(message, channel))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_WEBSOCKET_DESTINATION));
    }

    @Test
    void alternativeStompHandshakeAuthenticatesSameAsConnect() {
        when(authenticationService.authenticate("token")).thenReturn(
                new AccessTokenAuthenticationService.AuthenticatedAccessToken(2L, UserRole.USER, SESSION_ID, EXPIRY));
        var accessor = StompHeaderAccessor.create(StompCommand.STOMP);
        accessor.setNativeHeader("Authorization", "Bearer token");
        accessor.setLeaveMutable(true);
        var message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        inboundInterceptor().preSend(message, channel);

        assertThat(accessor.getUser()).isInstanceOfSatisfying(WebSocketPrincipal.class, user -> {
            assertThat(user.getUserId()).isEqualTo(2L);
            assertThat(user.getSessionId()).isEqualTo(SESSION_ID);
        });
    }

    @Test
    void rejectsConnectWithoutValidAuthentication() {
        Message<byte[]> message = stompMessage(StompCommand.CONNECT, null, null);

        assertThatThrownBy(() -> inboundInterceptor().preSend(message, channel))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.WEBSOCKET_UNAUTHORIZED));
    }

    @Test
    void rejectsRoomSubscriptionByNonParticipant() {
        givenActiveUser(2L);
        when(chatRoomRepository.existsById(7L)).thenReturn(true);
        when(chatRoomRepository.existsByRoomIdAndParticipantId(7L, 2L)).thenReturn(false);
        Message<byte[]> message = stompMessage(
                StompCommand.SUBSCRIBE,
                "/topic/room/7",
                principal(2L)
        );

        assertThatThrownBy(() -> inboundInterceptor().preSend(message, channel))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.NOT_CHAT_PARTICIPANT));
    }

    @Test
    void rejectsInvalidSubscriptionDestination() {
        givenActiveUser(2L);
        Message<byte[]> message = stompMessage(
                StompCommand.SUBSCRIBE,
                "/topic/unknown/7",
                principal(2L)
        );

        assertThatThrownBy(() -> inboundInterceptor().preSend(message, channel))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_WEBSOCKET_DESTINATION));
    }

    @Test
    void allowsOnlyExactChatErrorUserDestination() {
        givenActiveUser(2L);
        Message<byte[]> allowed = stompMessage(
                StompCommand.SUBSCRIBE,
                "/user/queue/chat-errors",
                principal(2L)
        );

        assertThat(inboundInterceptor().preSend(allowed, channel)).isSameAs(allowed);

        Message<byte[]> arbitraryUserDestination = stompMessage(
                StompCommand.SUBSCRIBE,
                "/user/queue/other-errors",
                principal(2L)
        );

        assertThatThrownBy(() -> inboundInterceptor().preSend(arbitraryUserDestination, channel))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_WEBSOCKET_DESTINATION));
    }

    private void givenActiveUser(Long userId) {
        when(authenticationService.validateSession(userId, SESSION_ID, EXPIRY))
                .thenReturn(new AccessTokenAuthenticationService.AuthenticatedAccessToken(
                        userId, UserRole.USER, SESSION_ID, EXPIRY));
    }

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final Instant EXPIRY = Instant.now().plusSeconds(900);

    private WebSocketPrincipal principal(Long userId) {
        return new WebSocketPrincipal(userId, SESSION_ID, EXPIRY);
    }

    private Message<byte[]> stompMessage(StompCommand command, String destination, WebSocketPrincipal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setUser(principal);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private org.springframework.messaging.support.ChannelInterceptor inboundInterceptor() {
        return ReflectionTestUtils.invokeMethod(config, "createInboundInterceptor");
    }
}
