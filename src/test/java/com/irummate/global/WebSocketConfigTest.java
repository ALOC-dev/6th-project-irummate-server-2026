package com.irummate.global;

import com.irummate.domain.chat.repository.ChatRoomRepository;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.config.WebSocketConfig;
import com.irummate.global.jwt.JwtTokenProvider;
import com.irummate.global.jwt.WebSocketPrincipal;
import com.irummate.global.util.HashIdsUtils;
import com.irummate.global.websocket.WebSocketStompErrorHandler;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketConfigTest {

    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final ChatRoomRepository chatRoomRepository = mock(ChatRoomRepository.class);
    private final UsersRepository usersRepository = mock(UsersRepository.class);
    private final HashIdsUtils hashIdsUtils = mock(HashIdsUtils.class);
    private final WebSocketConfig config = new WebSocketConfig(
            jwtTokenProvider,
            chatRoomRepository,
            usersRepository,
            hashIdsUtils,
            mock(WebSocketStompErrorHandler.class)
    );
    private final MessageChannel channel = mock(MessageChannel.class);

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
        when(chatRoomRepository.existsById(7L)).thenReturn(true);
        when(chatRoomRepository.existsByRoomIdAndParticipantId(7L, 2L)).thenReturn(false);
        Message<byte[]> message = stompMessage(
                StompCommand.SUBSCRIBE,
                "/topic/room/7",
                new WebSocketPrincipal(2L)
        );

        assertThatThrownBy(() -> inboundInterceptor().preSend(message, channel))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.NOT_CHAT_PARTICIPANT));
    }

    @Test
    void rejectsInvalidSubscriptionDestination() {
        Message<byte[]> message = stompMessage(
                StompCommand.SUBSCRIBE,
                "/topic/unknown/7",
                new WebSocketPrincipal(2L)
        );

        assertThatThrownBy(() -> inboundInterceptor().preSend(message, channel))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_WEBSOCKET_DESTINATION));
    }

    @Test
    void allowsOnlyExactChatErrorUserDestination() {
        Message<byte[]> allowed = stompMessage(
                StompCommand.SUBSCRIBE,
                "/user/queue/chat-errors",
                new WebSocketPrincipal(2L)
        );

        assertThat(inboundInterceptor().preSend(allowed, channel)).isSameAs(allowed);

        Message<byte[]> arbitraryUserDestination = stompMessage(
                StompCommand.SUBSCRIBE,
                "/user/queue/other-errors",
                new WebSocketPrincipal(2L)
        );

        assertThatThrownBy(() -> inboundInterceptor().preSend(arbitraryUserDestination, channel))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_WEBSOCKET_DESTINATION));
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
