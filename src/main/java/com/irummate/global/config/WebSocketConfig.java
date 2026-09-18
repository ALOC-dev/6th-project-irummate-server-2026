package com.irummate.global.config;

import com.irummate.domain.chat.repository.ChatRoomRepository;
import com.irummate.domain.user.entity.UserStatus;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.jwt.JwtTokenProvider;
import com.irummate.global.jwt.WebSocketPrincipal;
import com.irummate.global.util.HashIdsUtils;
import io.jsonwebtoken.Claims;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROOM_TOPIC_PREFIX = "/topic/room/";
    private static final String USER_QUEUE_PREFIX = "/queue/user/";

    private final JwtTokenProvider jwtTokenProvider;
    private final ChatRoomRepository chatRoomRepository;
    private final UsersRepository usersRepository;
    private final HashIdsUtils hashIdsUtils;

    public WebSocketConfig(JwtTokenProvider jwtTokenProvider,
                            ChatRoomRepository chatRoomRepository,
                            UsersRepository usersRepository,
                            HashIdsUtils hashIdsUtils) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.chatRoomRepository = chatRoomRepository;
        this.usersRepository = usersRepository;
        this.hashIdsUtils = hashIdsUtils;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 프론트에서 최초로 웹소켓 연결을 맺는 endpoint
        registry.addEndpoint("/ws/chat")
                // 프론트 로컬 개발 서버와 백엔드 정적 테스트 페이지에서만 웹소켓 연결을 허용한다.
                .setAllowedOriginPatterns(
                        "https://www.irummate.com",
                        "https://irummate.com"
                )
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // [SUB] 프론트에서 메시지를 수신하기 위해 구독하는 주소 prefix
        // /topic은 채팅방 단위 브로드캐스트, /queue는 개인 알림용으로 사용한다.
        registry.enableSimpleBroker("/topic", "/queue");

        // [PUB] 프론트에서 백엔드로 메시지를 보낼 때 사용하는 주소 prefix
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor == null) {
                    return message;
                }

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String token = resolveToken(accessor);

                    if (token == null || !jwtTokenProvider.validateAccessToken(token)) {
                        throw new IllegalArgumentException("웹소켓 인증에 실패했습니다.");
                    }

                    Claims claims = jwtTokenProvider.parseClaims(token);
                    Long userId = hashIdsUtils.decode(claims.getSubject());

                    if (!isActiveUser(userId)) {
                        throw new IllegalArgumentException("WebSocket user is not active.");
                    }

                    accessor.setUser(new WebSocketPrincipal(userId));
                }

                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    validateSubscribe(accessor);
                }

                return message;
            }
        });
    }

    private boolean isActiveUser(Long userId) {
        return usersRepository.findById(userId)
                .map(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElse(false);
    }

    private void validateSubscribe(StompHeaderAccessor accessor) {
        Long userId = resolveUserId(accessor.getUser());
        String destination = accessor.getDestination();

        if (destination == null) {
            return;
        }

        if (destination.startsWith(ROOM_TOPIC_PREFIX)) {
            Long roomId = parseDestinationId(destination, ROOM_TOPIC_PREFIX);

            if (!chatRoomRepository.existsByRoomIdAndParticipantId(roomId, userId)) {
                throw new IllegalArgumentException("구독 권한이 없는 채팅방입니다.");
            }

            return;
        }

        if (destination.startsWith(USER_QUEUE_PREFIX)) {
            Long destinationUserId = parseHashedDestinationUserId(destination);

            if (!destinationUserId.equals(userId)) {
                throw new IllegalArgumentException("구독 권한이 없는 개인 알림입니다.");
            }

            return;
        }

        throw new IllegalArgumentException("Invalid subscription destination.");
    }

    private Long resolveUserId(Principal principal) {
        if (principal instanceof WebSocketPrincipal webSocketPrincipal) {
            return webSocketPrincipal.getUserId();
        }

        throw new IllegalArgumentException("웹소켓 인증에 실패했습니다.");
    }

    private Long parseDestinationId(String destination, String prefix) {
        try {
            return Long.valueOf(destination.substring(prefix.length()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("잘못된 구독 주소입니다.");
        }
    }

    private Long parseHashedDestinationUserId(String destination) {
        try {
            return hashIdsUtils.decode(destination.substring(USER_QUEUE_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("잘못된 구독 주소입니다.");
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
