package com.irummate.global.config;

import com.irummate.domain.chat.service.ChatSocketSessions;
import com.irummate.domain.user.entity.UserRole;
import com.irummate.global.security.AccessTokenAuthenticationService;
import com.irummate.global.jwt.WebSocketPrincipal;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.websocket.WebSocketStompErrorHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CHAT_ERROR_DESTINATION = "/user/queue/chat-errors";
    private static final String CHAT_SEND_DESTINATION = "/app/chat/send";

    private final AccessTokenAuthenticationService authenticationService;
    private final WebSocketStompErrorHandler stompErrorHandler;
    private final ChatSocketSessions chatSockets;

    public WebSocketConfig(AccessTokenAuthenticationService authenticationService,
                            WebSocketStompErrorHandler stompErrorHandler,
                            ChatSocketSessions chatSockets) {
        this.authenticationService = authenticationService;
        this.stompErrorHandler = stompErrorHandler;
        this.chatSockets = chatSockets;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.setErrorHandler(stompErrorHandler);

        // 프론트에서 최초로 웹소켓 연결을 맺는 endpoint
        registry.addEndpoint("/ws/chat")
                // 프론트 로컬 개발 서버와 백엔드 정적 테스트 페이지에서만 웹소켓 연결을 허용한다.
                .setAllowedOriginPatterns(
                        "https://localhost",     // Android
                        "capacitor://localhost", // iOS
                        "http://localhost.com:5173",
                        "https://www.irummate.com",
                        "https://irummate.com"
                )
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 사용자 목적지를 해석한 뒤 연결별 큐로 전달한다.
        registry.enableSimpleBroker("/queue");

        // [PUB] 프론트에서 백엔드로 메시지를 보낼 때 사용하는 주소 prefix
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(createInboundInterceptor());
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ExecutorChannelInterceptor() {
            @Override
            public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
                return chatSockets.allowsOutbound(message) ? message : null;
            }
        });
    }

    ChannelInterceptor createInboundInterceptor() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor == null) {
                    return message;
                }

                if (StompCommand.CONNECT.equals(accessor.getCommand()) || StompCommand.STOMP.equals(accessor.getCommand())) {
                    String token = resolveToken(accessor);

                    if (token == null) {
                        throw new BusinessException(ErrorCode.WEBSOCKET_UNAUTHORIZED);
                    }

                    try {
                        var access = authenticationService.authenticate(token);
                        requireUserRole(access.role());
                        accessor.setUser(new WebSocketPrincipal(access.userId(), access.sessionId(), access.expiresAt()));
                    } catch (BusinessException e) {
                        throw new BusinessException(ErrorCode.WEBSOCKET_UNAUTHORIZED);
                    }
                }

                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    validateConnection(accessor.getUser());
                    validateSubscribe(accessor);
                }

                if (SimpMessageType.MESSAGE.equals(accessor.getMessageType())) {
                    validateConnection(accessor.getUser());
                    // MESSAGE is a server frame, but Spring also maps it to SimpMessageType.MESSAGE.
                    // Require a client SEND, not just a matching destination, before reaching the broker.
                    if (!StompCommand.SEND.equals(accessor.getCommand())
                            || !CHAT_SEND_DESTINATION.equals(accessor.getDestination())) {
                        throw new BusinessException(ErrorCode.INVALID_WEBSOCKET_DESTINATION);
                    }
                }

                return message;
            }
        };
    }

    private void validateConnection(Principal principal) {
        if (!(principal instanceof WebSocketPrincipal user)) {
            throw new BusinessException(ErrorCode.WEBSOCKET_UNAUTHORIZED);
        }
        try {
            var access = authenticationService.validateSession(user.getUserId(), user.getSessionId(), user.getExpiresAt());
            requireUserRole(access.role());
        } catch (BusinessException e) {
            throw new BusinessException(ErrorCode.WEBSOCKET_UNAUTHORIZED);
        }
    }

    private void validateSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (CHAT_ERROR_DESTINATION.equals(destination)
                || ("/user" + ChatSocketSessions.EVENTS_QUEUE).equals(destination)
                || ("/user" + ChatSocketSessions.NOTIFICATIONS_QUEUE).equals(destination)) {
            return;
        }

        throw new BusinessException(ErrorCode.INVALID_WEBSOCKET_DESTINATION);
    }

    private void requireUserRole(UserRole role) {
        if (role != UserRole.USER) {
            throw new BusinessException(ErrorCode.WEBSOCKET_UNAUTHORIZED);
        }
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");

        if (authorization == null) {
            authorization = accessor.getFirstNativeHeader("authorization");
        }

        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length());
        }

        return null;
    }
}
