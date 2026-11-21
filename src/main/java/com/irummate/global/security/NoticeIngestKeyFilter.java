package com.irummate.global.security;

import com.irummate.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class NoticeIngestKeyFilter extends OncePerRequestFilter {
    private final byte[] expectedDigest;
    private final boolean configured;
    private final SecurityErrorResponseWriter errorWriter;

    public NoticeIngestKeyFilter(String key, SecurityErrorResponseWriter errorWriter) {
        configured = !key.isBlank();
        expectedDigest = digest(key);
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader("X-Ingest-Key");
        if (!configured || key == null || !MessageDigest.isEqual(expectedDigest, digest(key))) {
            errorWriter.write(response, ErrorCode.UNAUTHORIZED, "수집 API 인증에 실패했습니다.");
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
