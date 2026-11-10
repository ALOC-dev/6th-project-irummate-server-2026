package com.irummate;

import com.irummate.global.jwt.JwtTokenProvider;
import com.irummate.global.util.HashIdsUtils;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

@Import(IrummateApplicationTests.PostgresConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=irummate-test-secret-key-with-at-least-32-bytes",
        "jwt.access-token-expiration=3600000", "jwt.refresh-token-expiration=1209600000",
        "hashids.salt=irummate-test-salt", "hashids.minlength=8",
        "kakao.client-id=test-client", "kakao.client-secret=test-secret",
        "kakao.redirect-uri=http://localhost/api/auth/kakao/callback", "kakao.admin-key=test-admin-key",
        "cloud.aws.region=ap-northeast-2", "cloud.aws.s3.bucket=irummate-test-bucket"
})
abstract class SecurityIntegrationSupport {
    static final String SECRET = "irummate-test-secret-key-with-at-least-32-bytes";
    static final long ALICE = 1001L;
    static final long BOB = 1002L;
    static final long OUTSIDER = 1003L;

    @MockitoBean S3Client s3Client;
    @MockitoBean S3Presigner s3Presigner;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired HashIdsUtils hashIds;
    @Autowired JsonMapper json;
    @LocalServerPort int port;

    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    UUID aliceSession;
    UUID bobSession;
    UUID outsiderSession;
    long roomId;

    @BeforeEach
    void createSecurityFixtures() throws Exception {
        // This context uses its own disposable Testcontainers database, never a local .env database.
        jdbc.execute("TRUNCATE users RESTART IDENTITY CASCADE");
        for (long id : new long[]{ALICE, BOB, OUTSIDER}) {
            jdbc.update("""
                    INSERT INTO users(id, oauth_id, nickname, role, status, created_at, updated_at)
                    VALUES (?, ?, ?, 'USER', 'ACTIVE', now(), now())
                    """, id, "security-" + id, "user-" + id);
        }
        aliceSession = session(ALICE, "alice-refresh");
        bobSession = session(BOB, "bob-refresh");
        outsiderSession = session(OUTSIDER, "outsider-refresh");
        Long matchId = jdbc.queryForObject("""
                INSERT INTO match_requests(user_low_id, user_high_id, user_low_status, user_high_status,
                                           match_percentage, created_at)
                VALUES (?, ?, 'HEART', 'HEART', 90, now()) RETURNING match_request_id
                """, Long.class, ALICE, BOB);
        roomId = jdbc.queryForObject("""
                INSERT INTO chat_rooms(match_request_id, status, created_at)
                VALUES (?, 'OPEN', now()) RETURNING id
                """, Long.class, matchId);
    }

    private UUID session(long userId, String refreshToken) throws Exception {
        UUID id = UUID.randomUUID();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(refreshToken.getBytes(StandardCharsets.UTF_8)));
        jdbc.update("""
                INSERT INTO login_sessions(id, user_id, refresh_token_hash, expires_at, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, id, userId, hash, LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(1),
                LocalDateTime.now(ZoneId.of("Asia/Seoul")));
        return id;
    }

    String access(long userId, UUID sessionId) {
        return tokens.createAccessToken(hashIds.encode(userId), "USER", sessionId.toString());
    }

    String customToken(String subject, String sessionId, String type, Instant expiry, String secret) {
        return Jwts.builder().subject(subject).claim("role", "ADMIN").claim("sid", sessionId)
                .claim("type", type).issuedAt(Date.from(Instant.now())).expiration(Date.from(expiry))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }

    HttpResponse<String> request(String method, String path, String token, String cookie) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10)).method(method, HttpRequest.BodyPublishers.noBody());
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (cookie != null) request.header("Cookie", cookie);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
