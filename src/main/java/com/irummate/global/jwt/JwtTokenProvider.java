package com.irummate.global.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private final JwtProperties jwtProperties;
    private SecretKey secretKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @PostConstruct
    void init() {
        // 서버 시작후jwt.scret값을 읽어서 비밀키 객체 만들어줌
        //this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getSecret()));
        this.secretKey = Keys.hmacShaKeyFor(
                jwtProperties.getSecret().getBytes()
        );
    }
    //user id랑 role을 넣어서 access토큰 생성
    public String createAccessToken(String userId, String role) {
        return createToken(userId, role, ACCESS_TOKEN_TYPE, jwtProperties.getAccessTokenExpiration());
    }
    //refresh토큰 생성
    public String createRefreshToken(String userId, String role) {
        return createToken(userId, role, REFRESH_TOKEN_TYPE, jwtProperties.getRefreshTokenExpiration());
    }
    //토큰 검증
    public boolean validateToken(String token) {
        try {
            // 파싱 과정에서 서명과 만료 시간이 함께 검증된다.
            parseClaims(token);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
    //토큰 내용 꺼내기
    public boolean validateAccessToken(String token) {
        return validateTokenType(token, ACCESS_TOKEN_TYPE);
    }

    public boolean validateRefreshToken(String token) {
        return validateTokenType(token, REFRESH_TOKEN_TYPE);
    }

    private boolean validateTokenType(String token, String tokenType) {
        try {
            Claims claims = parseClaims(token);
            return tokenType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
        } catch (RuntimeException e) {
            return false;
        }
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getAccessTokenExpiration() {
        return jwtProperties.getAccessTokenExpiration();
    }

    public Long getRefreshTokenExpiration() {
        return jwtProperties.getRefreshTokenExpiration();
    }
    //실제 토큰 만들어줌
    private String createToken(String userId, String role, String tokenType, Long expirationMillis) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + expirationMillis);

        // subject에는 로컬 사용자 ID를 저장하고, role은 권한 확인용 claim으로 저장한다.
        return Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(now)
                .expiration(expiresAt)
                .signWith(secretKey)
                .compact();
    }
    /*
    JWT payload
    {
    "sub": "1",
    "role": "USER",
    "iat": 1710000000,
    "exp": 1710003600
    }
     */
}
