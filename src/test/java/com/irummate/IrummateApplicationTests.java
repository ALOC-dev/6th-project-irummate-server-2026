package com.irummate;

import com.irummate.domain.auth.dto.KakaoTokenResponseDto;
import com.irummate.domain.auth.dto.KakaoUserInfoResponseDto;
import com.irummate.domain.admin.service.AdminUserService;
import com.irummate.domain.admin.service.AdminCertificationService;
import com.irummate.domain.admin.dto.AdminCertificationApproveRequestDto;
import com.irummate.domain.certification.dto.CertificationRequestDto;
import com.irummate.domain.certification.service.CertificationService;
import com.irummate.domain.certification.service.CertificationLifecycleService;
import com.irummate.domain.certification.entity.Certification;
import com.irummate.domain.certification.entity.CertificationStatus;
import com.irummate.domain.certification.repository.CertificationRepository;
import com.irummate.domain.chat.entity.ChatRoom;
import com.irummate.domain.chat.entity.ChatRoomMember;
import com.irummate.domain.chat.entity.ChatRoomType;
import com.irummate.domain.chat.service.ChatService;
import com.irummate.domain.chatbot.init.DormitoryFaqSeeder;
import com.irummate.domain.matching.entity.MatchRequests;
import com.irummate.domain.matching.entity.MatchStatus;
import com.irummate.domain.matching.service.MatchingService;
import com.irummate.domain.recruitment.entity.RecruitmentCategory;
import com.irummate.domain.recruitment.entity.RecruitmentStatus;
import com.irummate.domain.recruitment.entity.RecruitmentVisibility;
import com.irummate.domain.recruitment.entity.Recruitments;
import com.irummate.domain.recruitment.scheduler.RecruitmentScheduler;
import com.irummate.domain.survey.dto.SurveyAnswers;
import com.irummate.domain.survey.entity.SurveyAnswerField;
import com.irummate.domain.survey.entity.UserPreferences;
import com.irummate.domain.survey.repository.UserPreferencesRepository;
import com.irummate.domain.user.entity.Users;
import com.irummate.global.jwt.JwtTokenProvider;
import com.irummate.global.util.HashIdsUtils;
import com.irummate.global.util.SemesterUtils;
import com.openai.client.OpenAIClient;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.s3.S3Utils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    // 일반 테스트는 빈 DB에서도 FAQ 적재 및 외부 API 호출을 실행하지 않는다.
    @MockitoBean
    private DormitoryFaqSeeder dormitoryFaqSeeder;

    @MockitoBean
    private OpenAIClient openAIClient;

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

    @MockitoBean
    private S3Utils s3Utils;

    // 예약 작업이 테스트 중이나 DB 종료 시점에 실제 트랜잭션을 시작하지 않도록 격리
    @MockitoBean
    private RecruitmentScheduler recruitmentScheduler;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserPreferencesRepository preferencesRepository;

    @Autowired
    private CertificationRepository certificationRepository;

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private AdminCertificationService adminCertificationService;

    @Autowired
    private CertificationService certificationService;

    @Autowired
    private CertificationLifecycleService certificationLifecycleService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private HashIdsUtils hashIdsUtils;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private MatchingService matchingService;

    @Autowired
    private ChatService chatService;

    @Test
    @Transactional
    void mutualHeartsCreateOneRoomWithBothMembersAndPreserveChatFlow() {
        MatchRequests request = persistChatMatch("mutual-hearts", MatchStatus.RECOMMENDED);
        Users first = request.getUserLow();
        Users second = request.getUserHigh();

        matchingService.heart(first.getId(), second.getId());
        assertThat(chatService.getChatRooms(first.getId()).getRooms()).isEmpty();
        matchingService.heart(second.getId(), first.getId());

        var room = chatService.createChatRoomIfNotExists(request.getId());
        assertThat(room.getType()).isEqualTo(ChatRoomType.MATCH);
        assertThat(jdbcTemplate.queryForList("""
                SELECT user_id FROM chat_room_members
                WHERE room_id = ? AND left_at IS NULL AND last_read_message_id IS NULL
                ORDER BY user_id
                """, Long.class, room.getId())).containsExactly(first.getId(), second.getId());
        assertThat(chatService.getChatRooms(first.getId()).getRooms()).hasSize(1);

        var sent = chatService.sendMessage(room.getId(), first.getId(), "hello");
        var later = chatService.sendMessage(room.getId(), first.getId(), "not seen yet");
        assertThat(chatService.getTotalUnreadCountValue(second.getId())).isEqualTo(2);
        assertThat(chatService.markMessagesAsRead(room.getId(), second.getId(), sent.getMessageId()).event().getLastReadMessageId())
                .isEqualTo(sent.getMessageId());
        assertThat(chatService.getTotalUnreadCountValue(second.getId())).isEqualTo(1);
        assertThat(chatService.getMessages(room.getId(), first.getId(), null, 10).getMessages())
                .extracting(message -> message.getIsRead()).containsExactly(false, true);
        assertThat(chatService.getMessages(room.getId(), second.getId(), null, 10).getMessages())
                .extracting(message -> message.getIsRead()).containsExactly(false, true);

        assertThat(chatService.markMessagesAsRead(room.getId(), second.getId(), later.getMessageId()).event()).isNotNull();
        assertThat(chatService.markMessagesAsRead(room.getId(), second.getId(), sent.getMessageId()).event()).isNull();
        assertThat(chatService.markMessagesAsRead(room.getId(), second.getId(), later.getMessageId()).event()).isNull();
        assertThat(chatService.getTotalUnreadCountValue(second.getId())).isZero();
    }

    @Test
    @Transactional
    void matchingParticipantsLoseChatAccessWhenTheirMembershipEnds() {
        MatchRequests request = persistChatMatch("former-chat-member", MatchStatus.HEART);
        Long senderId = request.getUserLow().getId();
        Long formerMemberId = request.getUserHigh().getId();
        ChatRoom room = chatService.createChatRoomIfNotExists(request.getId());
        long messageId = chatService.sendMessage(room.getId(), senderId, "hello").getMessageId();
        assertThat(chatService.getTotalUnreadCountValue(formerMemberId)).isEqualTo(1);

        chatService.closeMatchRoomByMatchRequestId(request.getId());
        assertThat(chatService.getMessages(room.getId(), formerMemberId, null, 10).getMessages()).hasSize(1);
        assertThat(assertThrows(BusinessException.class,
                () -> chatService.sendMessage(room.getId(), formerMemberId, "closed")).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_ROOM_CLOSED);

        jdbcTemplate.update("UPDATE chat_room_members SET left_at = CURRENT_TIMESTAMP WHERE room_id = ? AND user_id = ?",
                room.getId(), formerMemberId);

        assertThat(chatService.getChatRooms(formerMemberId).getRooms()).isEmpty();
        assertThat(chatService.getTotalUnreadCountValue(formerMemberId)).isZero();
        assertThat(assertThrows(BusinessException.class,
                () -> chatService.getMessages(room.getId(), formerMemberId, null, 10)).getErrorCode())
                .isEqualTo(ErrorCode.NOT_CHAT_PARTICIPANT);
        assertThat(assertThrows(BusinessException.class,
                () -> chatService.markMessagesAsRead(room.getId(), formerMemberId, messageId)).getErrorCode())
                .isEqualTo(ErrorCode.NOT_CHAT_PARTICIPANT);
        assertThat(assertThrows(BusinessException.class,
                () -> chatService.sendMessage(room.getId(), formerMemberId, "left")).getErrorCode())
                .isEqualTo(ErrorCode.NOT_CHAT_PARTICIPANT);
    }

    @Test
    @Transactional
    void finalMatchConfirmationClosesOnlyOtherMatchRooms() {
        long firstId = savePreferences("chat-confirm-first", 1, 1, "FEMALE");
        long secondId = savePreferences("chat-confirm-second", 1, 1, "FEMALE");
        long thirdId = savePreferences("chat-confirm-third", 1, 1, "FEMALE");
        MatchRequests selected = persistHeartMatch(firstId, secondId);
        MatchRequests other = persistHeartMatch(firstId, thirdId);
        long selectedRoomId = chatService.createChatRoomIfNotExists(selected.getId()).getId();
        long otherRoomId = chatService.createChatRoomIfNotExists(other.getId()).getId();

        long recruitmentRoomId = persistRecruitmentChat(selected.getUserLow(), List.of(selected.getUserLow())).getId();

        matchingService.confirm(firstId, secondId);
        matchingService.confirm(secondId, firstId);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(ChatRoom.class, selectedRoomId).isClosed()).isFalse();
        assertThat(entityManager.find(ChatRoom.class, otherRoomId).isClosed()).isTrue();
        assertThat(entityManager.find(ChatRoom.class, recruitmentRoomId).isClosed()).isFalse();
    }

    @Test
    @Transactional
    void recruitmentReadCursorChangesOnlyTheReadersUnreadCount() {
        MatchRequests match = persistChatMatch("group-chat-read", MatchStatus.HEART);
        Users first = match.getUserLow();
        Users second = match.getUserHigh();
        Users third = Users.builder().oauthId("group-chat-third").nickname("third").build();
        entityManager.persist(third);
        long matchRoomId = chatService.createChatRoomIfNotExists(match.getId()).getId();
        long matchMessageId = chatService.sendMessage(matchRoomId, first.getId(), "MATCH message").getMessageId();
        ChatRoom groupRoom = persistRecruitmentChat(first, List.of(first, second, third));
        var groupMessage = chatService.sendMessage(groupRoom.getId(), first.getId(), "RECRUITMENT message");

        assertThat(chatService.markMessagesAsRead(groupRoom.getId(), second.getId(), groupMessage.getMessageId()).event())
                .isNotNull();
        var rooms = chatService.getChatRooms(second.getId()).getRooms();
        assertThat(rooms).hasSize(2);
        var groupItem = rooms.stream().filter(room -> room.getType() == ChatRoomType.RECRUITMENT).findFirst().orElseThrow();
        assertThat(groupItem.getRecruitment().recruitmentId()).isEqualTo(groupRoom.getRecruitment().getId());
        assertThat(groupItem.getRecruitment().participantCount()).isEqualTo(3);
        assertThat(groupItem.getUnreadCount()).isZero();
        assertThat(chatService.getTotalUnreadCountValue(second.getId())).isEqualTo(1);
        assertThat(chatService.getTotalUnreadCountValue(first.getId())).isZero();
        assertThat(chatService.getTotalUnreadCountValue(third.getId())).isEqualTo(1);
        assertThat(chatService.createNotification(groupRoom.getId(), first.getId(), third.getId(), groupMessage)
                .getUnreadCount()).isEqualTo(1);
        assertThat(chatService.getMessages(groupRoom.getId(), third.getId(), null, 10).getMessages().getFirst().getIsRead())
                .isNull();
        assertThat(assertThrows(BusinessException.class,
                () -> chatService.markMessagesAsRead(groupRoom.getId(), second.getId(), matchMessageId)).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @Transactional
    void readEndpointRequiresAnExplicitPositiveMessageId() throws Exception {
        MatchRequests match = persistChatMatch("chat-read-endpoint", MatchStatus.HEART);
        long readerId = match.getUserHigh().getId();
        long roomId = chatService.createChatRoomIfNotExists(match.getId()).getId();
        long messageId = chatService.sendMessage(roomId, match.getUserLow().getId(), "hello").getMessageId();
        UUID sessionId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO login_sessions(id, user_id, refresh_token_hash, expires_at, created_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '1 day', CURRENT_TIMESTAMP)
                """, sessionId, readerId, sessionId.toString());
        String token = jwtTokenProvider.createAccessToken(hashIdsUtils.encode(readerId), "USER", sessionId.toString());

        for (String body : List.of("", "{}", "{\"lastReadMessageId\":0}", "{malformed")) {
            mockMvc.perform(patch("/api/chat/rooms/{roomId}/read", roomId)
                            .header("Authorization", "Bearer " + token)
                            .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(chatService.getTotalUnreadCountValue(readerId)).isEqualTo(1);
        mockMvc.perform(patch("/api/chat/rooms/{roomId}/read", roomId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"lastReadMessageId\":" + messageId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.success").value(true));
        assertThat(chatService.getTotalUnreadCountValue(readerId)).isZero();
    }

    private ChatRoom persistRecruitmentChat(Users author, List<Users> members) {
        LocalDateTime activityAt = LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(1);
        Recruitments recruitment = Recruitments.builder()
                .author(author)
                .title("chat test recruitment")
                .category(RecruitmentCategory.MEAL)
                .status(RecruitmentStatus.OPEN)
                .visibility(RecruitmentVisibility.VISIBLE)
                .activityAt(activityAt)
                .deadlineAt(activityAt.minusMinutes(30))
                .nowParticipants(members.size())
                .maxParticipants(3)
                .build();
        entityManager.persist(recruitment);
        ChatRoom recruitmentRoom = ChatRoom.forRecruitment(recruitment);
        entityManager.persist(recruitmentRoom);
        for (Users member : members) {
            entityManager.persist(ChatRoomMember.join(recruitmentRoom, member, recruitmentRoom.getCreatedAt()));
        }
        return recruitmentRoom;
    }

    private MatchRequests persistHeartMatch(long lowId, long highId) {
        MatchRequests request = MatchRequests.builder()
                .userLow(entityManager.getReference(Users.class, lowId))
                .userHigh(entityManager.getReference(Users.class, highId))
                .userLowPreferences(preferencesRepository.getReferenceById(lowId))
                .userHighPreferences(preferencesRepository.getReferenceById(highId))
                .userLowStatus(MatchStatus.HEART)
                .userHighStatus(MatchStatus.HEART)
                .build();
        entityManager.persist(request);
        return request;
    }

    @Test
    void simultaneousRoomCreationReusesTheSameRoom() throws Exception {
        var transaction = new TransactionTemplate(transactionManager);
        String fixture = "parallel-chat-" + UUID.randomUUID();
        Long requestId = transaction.execute(status -> persistChatMatch(fixture, MatchStatus.HEART).getId());

        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            Callable<Long> createRoom = () -> {
                ready.countDown();
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                return chatService.createChatRoomIfNotExists(requestId).getId();
            };
            var first = executor.submit(createRoom);
            var second = executor.submit(createRoom);

            Long roomId = first.get(10, TimeUnit.SECONDS);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(roomId);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_room_members WHERE room_id = ?",
                    Long.class, roomId)).isEqualTo(2L);
        } finally {
            transaction.executeWithoutResult(status -> {
                jdbcTemplate.update("DELETE FROM chat_room_members WHERE room_id IN "
                        + "(SELECT id FROM chat_rooms WHERE match_request_id = ?)", requestId);
                jdbcTemplate.update("DELETE FROM chat_rooms WHERE match_request_id = ?", requestId);
                jdbcTemplate.update("DELETE FROM match_requests WHERE match_request_id = ?", requestId);
                jdbcTemplate.update("DELETE FROM users WHERE oauth_id IN (?, ?)", fixture + "-1", fixture + "-2");
            });
        }
    }

    private MatchRequests persistChatMatch(String fixture, MatchStatus initialStatus) {
        Users first = Users.builder().oauthId(fixture + "-1").nickname("first").build();
        Users second = Users.builder().oauthId(fixture + "-2").nickname("second").build();
        entityManager.persist(first);
        entityManager.persist(second);
        MatchRequests request = MatchRequests.builder()
                .userLow(first)
                .userHigh(second)
                .userLowStatus(initialStatus)
                .userHighStatus(initialStatus)
                .build();
        entityManager.persist(request);
        return request;
    }

    @Test
    @Transactional
    void chatRoomSchemaRejectsRecruitmentRoomWithoutARecruitment() {
        var invalidRoom = assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO chat_rooms (type, status, close_at, created_at)
                VALUES ('RECRUITMENT', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """));
        assertThat(invalidRoom.getMostSpecificCause().getMessage()).contains("ck_chat_rooms_type");
    }

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

    @Test
    @Transactional
    void certificationPublicUuidIsGeneratedAndQueryableAndSurvivesResubmission() {
        Users user = Users.builder().oauthId("certification-public-id").nickname("인증 사용자").build();
        entityManager.persist(user);
        Certification certification = Certification.builder()
                .user(user).semester("2026-2").imageKey("certifications/first.png").build();
        certificationRepository.saveAndFlush(certification);
        var publicId = certification.getPublicId();
        assertThat(publicId).isNotNull();
        entityManager.clear();

        Certification saved = certificationRepository.findByPublicId(publicId).orElseThrow();
        assertThat(saved.getId()).isEqualTo(certification.getId());
        saved.resubmit("certifications/second.png");
        entityManager.flush();
        entityManager.clear();

        Certification resubmitted = certificationRepository.findByPublicId(publicId).orElseThrow();
        assertThat(resubmitted.getPublicId()).isEqualTo(publicId);
        assertThat(resubmitted.getImageKey()).isEqualTo("certifications/second.png");
    }

    @ParameterizedTest
    @EnumSource(CertificationStatus.class)
    @Transactional
    void previousSemesterStatusIsNotUsedForCurrentCertification(CertificationStatus previousStatus) throws Exception {
        Users user = Users.builder().oauthId("previous-semester").nickname("이전 학기 사용자").build();
        entityManager.persist(user);
        String previousSemester = (LocalDate.now(ZoneId.of("Asia/Seoul")).getYear() - 1) + "-2";
        Certification previous = Certification.builder()
                .user(user).semester(previousSemester).imageKey("certifications/previous.png")
                .certificationStatus(previousStatus).build();
        if (previousStatus == CertificationStatus.APPROVED) {
            previous.approve(user, null, LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusDays(1));
        }
        certificationRepository.saveAndFlush(previous);
        String token = jwtTokenProvider.createAccessToken(hashIdsUtils.encode(user.getId()), "USER", "test-session");

        assertThat(adminUserService.getUser(hashIdsUtils.encode(user.getId())).getCertificationStatus())
                .isEqualTo("NONE");
        mockMvc.perform(get("/api/certifications/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.status").value("NONE"))
                .andExpect(jsonPath("data.certificationId").doesNotExist());
        mockMvc.perform(get("/api/auth/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.user.certificationStatus").value("NONE"));

        entityManager.clear();
        assertThat(certificationRepository.findById(previous.getId()).orElseThrow().getCertificationStatus())
                .isEqualTo(previousStatus);
    }

    @ParameterizedTest
    @EnumSource(CertificationStatus.class)
    @Transactional
    void bothStatusEndpointsReturnCurrentSemesterStatus(CertificationStatus currentStatus) throws Exception {
        Users user = Users.builder().oauthId("current-semester").nickname("현재 학기 사용자").build();
        entityManager.persist(user);
        Certification current = Certification.builder()
                .user(user).semester(SemesterUtils.currentSemester()).imageKey("certifications/current.png")
                .certificationStatus(currentStatus).build();
        if (currentStatus == CertificationStatus.APPROVED) {
            current.approve(user, null, LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(1));
        }
        certificationRepository.saveAndFlush(current);
        String token = jwtTokenProvider.createAccessToken(hashIdsUtils.encode(user.getId()), "USER", "test-session");

        assertThat(adminUserService.getUser(hashIdsUtils.encode(user.getId())).getCertificationStatus())
                .isEqualTo(currentStatus.name());
        mockMvc.perform(get("/api/certifications/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.status").value(currentStatus.name()))
                .andExpect(jsonPath("data.semester").value(SemesterUtils.currentSemester()))
                .andExpect(jsonPath("data.certificationId").value(current.getPublicId().toString()));
        mockMvc.perform(get("/api/auth/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.user.certificationStatus").value(currentStatus.name()));
    }

    @Test
    @Transactional
    void expiredCurrentSemesterApprovalIsExpiredInBothStatusEndpoints() throws Exception {
        Users user = Users.builder().oauthId("expired-semester").nickname("만료 사용자").build();
        entityManager.persist(user);
        Certification current = Certification.builder()
                .user(user).semester(SemesterUtils.currentSemester()).imageKey("certifications/expired.png").build();
        current.approve(user, null, LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusDays(2));
        certificationRepository.saveAndFlush(current);
        String token = jwtTokenProvider.createAccessToken(hashIdsUtils.encode(user.getId()), "USER", "test-session");

        assertThat(adminUserService.getUser(hashIdsUtils.encode(user.getId())).getCertificationStatus())
                .isEqualTo("EXPIRED");
        assertThat(current.getCertificationStatus()).isEqualTo(CertificationStatus.APPROVED);
        // 로그인 상태 조회가 DB 상태 변경 없이도 만료를 판단하는지 먼저 확인
        mockMvc.perform(get("/api/auth/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.user.certificationStatus").value("EXPIRED"));
        mockMvc.perform(get("/api/certifications/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.status").value("EXPIRED"));
    }

    @Test
    void concurrentReviewCannotOverwriteCommittedDecision() {
        CertificationFixture fixture = saveCertificationFixture(CertificationStatus.PENDING, SemesterUtils.currentSemester());
        EntityManager first = entityManagerFactory.createEntityManager();
        EntityManager second = entityManagerFactory.createEntityManager();
        try {
            first.getTransaction().begin();
            second.getTransaction().begin();
            Certification firstReview = first.find(Certification.class, fixture.certificationId());
            Certification secondReview = second.find(Certification.class, fixture.certificationId());
            assertThat(firstReview.getVersion()).isEqualTo(0L);
            assertThat(secondReview.getVersion()).isEqualTo(0L);

            firstReview.approve(first.find(Users.class, fixture.userId()), null,
                    LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(1));
            first.getTransaction().commit();
            secondReview.reject(second.find(Users.class, fixture.userId()), "동시 거절");

            assertThrows(OptimisticLockException.class, second::flush);
            Certification saved = certificationRepository.findById(fixture.certificationId()).orElseThrow();
            assertThat(saved.getCertificationStatus()).isEqualTo(CertificationStatus.APPROVED);
            assertThat(saved.getVersion()).isEqualTo(1L);
        } finally {
            closeTransaction(first);
            closeTransaction(second);
            deleteCertificationFixture(fixture.userId());
        }
    }

    @Test
    void bulkExpirationIncrementsVersionAndRejectsStaleReview() {
        String previousSemester = (LocalDate.now(ZoneId.of("Asia/Seoul")).getYear() - 1) + "-2";
        CertificationFixture fixture = saveCertificationFixture(CertificationStatus.PENDING, previousSemester);
        EntityManager reviewer = entityManagerFactory.createEntityManager();
        try {
            reviewer.getTransaction().begin();
            Certification staleReview = reviewer.find(Certification.class, fixture.certificationId());

            certificationLifecycleService.expirePreviousSemesters();
            staleReview.reject(reviewer.find(Users.class, fixture.userId()), "지난 학기 심사");

            assertThrows(OptimisticLockException.class, reviewer::flush);
            Certification saved = certificationRepository.findById(fixture.certificationId()).orElseThrow();
            assertThat(saved.getCertificationStatus()).isEqualTo(CertificationStatus.EXPIRED);
            assertThat(saved.getVersion()).isEqualTo(1L);
        } finally {
            closeTransaction(reviewer);
            deleteCertificationFixture(fixture.userId());
        }
    }

    @Test
    void resubmissionInvalidatesOldReviewAndApprovalReturnsUpdatedVersion() {
        CertificationFixture fixture = saveCertificationFixture(CertificationStatus.REJECTED, SemesterUtils.currentSemester());
        try {
            CertificationRequestDto resubmission = new CertificationRequestDto();
            ReflectionTestUtils.setField(resubmission, "imageKey",
                    "certifications/" + SemesterUtils.currentSemester() + "/" + fixture.userId() + "/new.png");
            certificationService.createCertification(fixture.userId(), resubmission);
            AdminCertificationApproveRequestDto request = new AdminCertificationApproveRequestDto();
            ReflectionTestUtils.setField(request, "expectedVersion", 0L);

            BusinessException stale = assertThrows(BusinessException.class,
                    () -> adminCertificationService.approveCertification(fixture.userId(), fixture.publicId(), request));

            assertThat(stale.getErrorCode()).isEqualTo(ErrorCode.UPDATE_CONFLICT);
            Certification pending = certificationRepository.findById(fixture.certificationId()).orElseThrow();
            assertThat(pending.getCertificationStatus()).isEqualTo(CertificationStatus.PENDING);
            assertThat(pending.getVersion()).isEqualTo(1L);

            ReflectionTestUtils.setField(request, "expectedVersion", 1L);
            var approved = adminCertificationService.approveCertification(fixture.userId(), fixture.publicId(), request);
            assertThat(approved.getStatus()).isEqualTo("APPROVED");
            assertThat(approved.getVersion()).isEqualTo(2L);
        } finally {
            deleteCertificationFixture(fixture.userId());
        }
    }

    @Test
    void firstSubmissionLocksUserAndTimesOutConcurrentRequestWithoutDuplicateInsert() throws Exception {
        Long userId = new TransactionTemplate(transactionManager).execute(status ->
                savePreferences("concurrent-certification-" + UUID.randomUUID(), 1, 1, "FEMALE"));
        CountDownLatch firstValidating = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger validations = new AtomicInteger();
        doAnswer(invocation -> {
            validations.incrementAndGet();
            firstValidating.countDown();
            assertTrue(releaseFirst.await(10, TimeUnit.SECONDS));
            return null;
        }).when(s3Utils).validateUploadedImage(anyString());
        CertificationRequestDto request = new CertificationRequestDto();
        ReflectionTestUtils.setField(request, "imageKey",
                "certifications/" + SemesterUtils.currentSemester() + "/" + userId + "/first.png");

        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                var first = executor.submit(() -> certificationService.createCertification(userId, request));
                assertTrue(firstValidating.await(5, TimeUnit.SECONDS));
                var second = executor.submit(() -> certificationService.createCertification(userId, request));

                ExecutionException conflict = assertThrows(ExecutionException.class,
                        () -> second.get(5, TimeUnit.SECONDS));
                assertThat(conflict.getCause()).isInstanceOf(PessimisticLockingFailureException.class);
                assertThat(validations.get()).isEqualTo(1);

                releaseFirst.countDown();
                assertThat(first.get(5, TimeUnit.SECONDS).getCertificationId()).isNotNull();
                BusinessException duplicate = assertThrows(BusinessException.class,
                        () -> certificationService.createCertification(userId, request));
                assertThat(duplicate.getErrorCode()).isEqualTo(ErrorCode.CERTIFICATION_ALREADY_EXISTS);
                assertThat(certificationRepository.findAllByUser_IdOrderByCreatedAtDesc(userId)).hasSize(1);
            } finally {
                releaseFirst.countDown();
            }
        } finally {
            deleteCertificationFixture(userId);
        }
    }

    private CertificationFixture saveCertificationFixture(CertificationStatus status, String semester) {
        return new TransactionTemplate(transactionManager).execute(transaction -> {
            Long userId = savePreferences("certification-fixture-" + UUID.randomUUID(), 1, 1, "FEMALE");
            Certification certification = Certification.builder()
                    .user(entityManager.find(Users.class, userId)).semester(semester)
                    .imageKey("certifications/" + semester + "/" + userId + "/old.png")
                    .certificationStatus(status).build();
            certificationRepository.saveAndFlush(certification);
            return new CertificationFixture(userId, certification.getId(), certification.getPublicId());
        });
    }

    private void deleteCertificationFixture(Long userId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            jdbcTemplate.update("delete from certifications where user_id = ?", userId);
            jdbcTemplate.update("delete from user_preferences where user_id = ?", userId);
            jdbcTemplate.update("delete from user_details where user_id = ?", userId);
            jdbcTemplate.update("delete from users where id = ?", userId);
        });
    }

    private void closeTransaction(EntityManager manager) {
        if (manager.getTransaction().isActive()) {
            manager.getTransaction().rollback();
        }
        manager.close();
    }

    private record CertificationFixture(Long userId, Long certificationId, UUID publicId) { }

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
