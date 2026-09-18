package com.irummate.global.jwt;

import com.irummate.domain.user.entity.UserStatus;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.util.HashIdsUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final HashIdsUtils hashIdsUtils;
    private final UsersRepository usersRepository;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                   HashIdsUtils hashIdsUtils,
                                   UsersRepository usersRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.hashIdsUtils = hashIdsUtils;
        this.usersRepository = usersRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null && jwtTokenProvider.validateAccessToken(token)) {
            try {
                Claims claims = jwtTokenProvider.parseClaims(token);
                Long userId = hashIdsUtils.decode(claims.getSubject());
                String role = claims.get("role", String.class);

                boolean blocked = usersRepository.findById(userId)
                        .map(user -> user.getStatus() == UserStatus.BANNED || user.getStatus() == UserStatus.WITHDRAWN)
                        .orElse(true);

                if (!blocked) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userId,
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
                            );

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (RuntimeException e) {
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(AUTHORIZATION_HEADER);

        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length());
        }

        return null;
    }
}
