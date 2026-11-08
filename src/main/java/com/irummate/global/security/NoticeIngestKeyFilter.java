package com.irummate.global.security;

import tools.jackson.databind.json.JsonMapper;
import com.irummate.global.response.GlobalApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class NoticeIngestKeyFilter extends OncePerRequestFilter {
    private final byte[] expectedDigest;
    private final boolean configured;
    private final JsonMapper objectMapper;

    public NoticeIngestKeyFilter(String key, JsonMapper objectMapper) {
        configured = !key.isBlank();
        expectedDigest = digest(key);
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader("X-Ingest-Key");
        if (!configured || key == null || !MessageDigest.isEqual(expectedDigest, digest(key))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getWriter(), GlobalApiResponse.error(
                    HttpStatus.UNAUTHORIZED, "수집 API 인증에 실패했습니다.", null));
            return;
        }
        chain.doFilter(request, response);
    }

    private static byte[] digest(String key) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
