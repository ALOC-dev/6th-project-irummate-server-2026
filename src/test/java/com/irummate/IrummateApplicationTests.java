package com.irummate;

import com.irummate.domain.auth.dto.KakaoTokenResponseDto;
import com.irummate.domain.auth.dto.KakaoUserInfoResponseDto;
import com.irummate.domain.survey.dto.SurveyAnswers;
import com.irummate.domain.survey.entity.SurveyAnswerField;
import com.irummate.domain.survey.entity.UserPreferences;
import com.irummate.domain.survey.repository.UserPreferencesRepository;
import com.irummate.domain.user.entity.Users;
import com.irummate.global.jwt.JwtTokenProvider;
import com.irummate.global.util.HashIdsUtils;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IrummateApplicationTests.PostgresConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=irummate-test-secret-key-with-at-least-32-bytes",
        "jwt.access-token-expiration=3600000",
        "jwt.refresh-token-expiration=1209600000",
        "hashids.salt=irummate-test-salt",
        "hashids.minlength=8",
        "kakao.client-id=test-client",
        "kakao.client-secret=test-secret",
        "kakao.redirect-uri=http://localhost/api/auth/kakao/callback",
        "kakao.admin-key=test-admin-key",
        "cloud.aws.region=ap-northeast-2",
        "cloud.aws.s3.bucket=irummate-test-bucket",
        "notice.ingest-key=test-ingest-key"
})
class IrummateApplicationTests {

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresConfiguration {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
                    .withInitScript("db/test-init.sql");
        }
    }

    @MockitoBean
    private S3Client s3Client;

    @MockitoBean
    private S3Presigner s3Presigner;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserPreferencesRepository preferencesRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private HashIdsUtils hashIdsUtils;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @Transactional
    void roundTripsJsonbAndNineDimensionalVector() {
        Long userId = savePreferences("round-trip", 3, 1, "FEMALE");
        entityManager.clear();

        UserPreferences saved = preferencesRepository.findById(userId).orElseThrow();

        assertThat(saved.getAnswers().getBedtime()).isEqualTo(3);
        assertThat(saved.getAnswers().getShowerFrequency()).isEqualTo(1);
        assertThat(saved.getVisibleProfileFields()).containsExactly(SurveyAnswerField.BEDTIME);
        assertThat(saved.getLifestyleVector()).containsExactly(0.5f, 0, 0, 0, 0, 0, 0, 0, 0);
        assertThat(jdbcTemplate.queryForObject(
                "select pg_typeof(answers)::text from user_preferences where user_id = ?",
                String.class, userId)).isEqualTo("jsonb");
        assertThat(jdbcTemplate.queryForObject(
                "select vector_dims(lifestyle_vector) from user_preferences where user_id = ?",
                Integer.class, userId)).isEqualTo(9);
    }

    @Test
    @Transactional
    void recommendationsUseVectorDistanceAndSmokingAndGenderFilters() {
        Long me = savePreferences("me", 1, 1, "FEMALE");
        Long far = savePreferences("far", 5, 1, "FEMALE");
        Long near = savePreferences("near", 2, 1, "FEMALE");
        Long differentSmoking = savePreferences("smoking", 3, 2, "FEMALE");
        savePreferences("different-gender", 1, 1, "MALE");
        String vector = "[0,0,0,0,0,0,0,0,0]";

        var filtered = preferencesRepository.findNewRecommendationCandidates(me, "FEMALE", 1, vector, 10);
        assertThat(filtered).extracting(UserPreferencesRepository.RecommendationCandidate::getUserId)
                .containsExactly(near, far);
        assertThat(filtered.getFirst().getMatchPercentage()).isGreaterThan(filtered.getLast().getMatchPercentage());

        var relaxed = preferencesRepository.findNewRecommendationCandidatesIgnoringSmoking(me, "FEMALE", vector, 10);
        assertThat(relaxed).extracting(UserPreferencesRepository.RecommendationCandidate::getUserId)
                .containsExactly(near, differentSmoking, far);
    }

    @Test
    void ingestAuthenticationFailureKeepsJsonResponseContract() throws Exception {
        mockMvc.perform(post("/internal/notices"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("message").value("수집 API 인증에 실패했습니다."))
                .andExpect(jsonPath("code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("errors").doesNotExist());
    }

    @Test
    void mvcSerializesAnonymousAuthStatus() throws Exception {
        mockMvc.perform(get("/api/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.authenticated").value(false));
    }

    @Test
    void existingJwtAndHashidsContractStillWorks() {
        String publicId = hashIdsUtils.encode(42L);
        String token = jwtTokenProvider.createAccessToken(publicId, "USER", "test-session");

        assertThat(jwtTokenProvider.validateAccessToken(token)).isTrue();
        var claims = jwtTokenProvider.parseClaims(token);
        assertThat(hashIdsUtils.decode(claims.getSubject())).isEqualTo(42L);
        assertThat(claims.get("sid", String.class)).isEqualTo("test-session");
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
    }

    @Test
    void jacksonThreeReadsExistingKakaoJsonPropertyAnnotations() {
        var token = jsonMapper.readValue("""
                {"token_type":"bearer","access_token":"test-token","expires_in":3600}
                """, KakaoTokenResponseDto.class);
        var user = jsonMapper.readValue("""
                {"id":123,"kakao_account":{"email":"test@example.com",
                  "profile":{"nickname":"test","profile_image_url":"https://example.com/profile.png"}}}
                """, KakaoUserInfoResponseDto.class);

        assertThat(token.getAccessToken()).isEqualTo("test-token");
        assertThat(token.getExpiresIn()).isEqualTo(3600);
        assertThat(user.getId()).isEqualTo(123L);
        assertThat(user.getKakaoAccount().getProfile().getProfileImageUrl())
                .isEqualTo("https://example.com/profile.png");
    }

    private Long savePreferences(String name, int bedtime, int smokingStatus, String gender) {
        Users user = Users.builder().oauthId(name).nickname(name).build();
        entityManager.persist(user);
        SurveyAnswers answers = SurveyAnswers.builder()
                .bedtime(bedtime).snoring(1).sleepTalking(1).organizingStyle(1)
                .temperaturePreference(1).showerFrequency(1).speakerStyle(1)
                .callInRoom(1).eatingInRoom(1).build();
        entityManager.persist(UserPreferences.builder()
                .user(user).answers(answers).smokingStatus(smokingStatus).isCompleted(true)
                .visibleProfileFields(List.of(SurveyAnswerField.BEDTIME)).build());
        entityManager.flush();
        jdbcTemplate.update("""
                insert into user_details (user_id, real_name, student_id, age, gender, department, phone_number)
                values (?, ?, ?, 21, ?, 'test', '01000000000')
                """, user.getId(), name, name, gender);
        return user.getId();
    }
}
