package com.irummate;

import com.irummate.domain.privacy.service.PrivacyConsentService;
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
import com.irummate.domain.matching.repository.MatchRepository;
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
import com.irummate.domain.user.entity.UserRole;
import com.irummate.domain.user.entity.UserStatus;
import com.irummate.global.jwt.JwtTokenProvider;
import com.irummate.global.util.HashIdsUtils;
import com.irummate.global.util.SemesterUtils;
import com.openai.client.OpenAIClient;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.s3.S3Utils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.authentication.TestingAuthenticationToken;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    private MatchRepository matchRepository;

    @Autowired
    private MatchingService matchingService;

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
    private PrivacyConsentService privacyConsentService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private HashIdsUtils hashIdsUtils;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private ChatService chatService;

    @Test
    @Transactional
    void mutualHeartsCreateOneRoomWithBothMembersAndPreserveChatFlow() {
        MatchRequests request = persistChatMatch("mutual-hearts", MatchStatus.RECOMMENDED);
        Users first = request.getUserLow();
        Users second = request.getUserHigh();
        saveApprovedMatchingCertifications(first.getId(), second.getId());

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
        saveApprovedMatchingCertifications(firstId, secondId);
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
    void surveyReadIsUnchangedAndActualAnswerChangesArePersisted() {
        Long userId = savePreferences("survey-value-comparison", 1, 1, "FEMALE");
        entityManager.clear();
        UserPreferences saved = preferencesRepository.findById(userId).orElseThrow();

        // 조회만 했으면 변경으로 판단하지 않음
        var session = entityManager.unwrap(org.hibernate.Session.class);
        assertThat(session.isDirty()).isFalse();
        entityManager.flush();

        // 실제 답변 변경은 감지하여 저장
        // 벡터 재계산은 updatePreferences()에서만 수행
        // (userPreferencesRecalculatesVectorAndSurveyUpdatedAtOnlyWhenSurveyChanges에서 검증)
        saved.getAnswers().setBedtime(5);
        assertThat(session.isDirty()).isTrue();
        entityManager.flush();
        entityManager.clear();

        UserPreferences updated = preferencesRepository.findById(userId).orElseThrow();
        assertThat(updated.getAnswers().getBedtime()).isEqualTo(5);
    }

    @Test
    @Transactional
    void recommendationsUseVectorDistanceAndSmokingAndGenderFilters() {
        Long me = savePreferences("me", 1, 1, "FEMALE");
        Long far = savePreferences("far", 5, 1, "FEMALE");
        Long near = savePreferences("near", 2, 1, "FEMALE");
        Long differentSmoking = savePreferences("smoking", 3, 2, "FEMALE");
        Long differentGender = savePreferences("different-gender", 1, 1, "MALE");
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        String semester = SemesterUtils.currentSemester();
        for (Long candidate : List.of(far, near, differentSmoking, differentGender)) {
            saveMatchingCertification(candidate, semester, CertificationStatus.APPROVED, now.plusDays(1));
        }
        String vector = "[0,0,0,0,0,0,0,0,0]";

        var filtered = preferencesRepository.findNewRecommendationCandidates(me, "FEMALE", 1, vector, semester, now, 10);
        assertThat(filtered).extracting(UserPreferencesRepository.RecommendationCandidate::getUserId)
                .containsExactly(near, far);
        assertThat(filtered.getFirst().getMatchPercentage()).isGreaterThan(filtered.getLast().getMatchPercentage());

        var relaxed = preferencesRepository.findNewRecommendationCandidatesIgnoringSmoking(me, "FEMALE", vector, semester, now, 10);
        assertThat(relaxed).extracting(UserPreferencesRepository.RecommendationCandidate::getUserId)
                .containsExactly(near, differentSmoking, far);
    }

    @ParameterizedTest
    @CsvSource({
            "APPROVED, current, future, true",
            "APPROVED, current, none, true",
            "APPROVED, current, past, false",
            "APPROVED, current, now, false",
            "APPROVED, previous, none, false",
            "PENDING, current, none, false",
            "REJECTED, current, none, false",
            "EXPIRED, current, none, false",
            "MISSING, current, none, false"
    })
    @Transactional
    void recommendationsRequireCurrentValidCertification(String status, String term, String expiry, boolean expected) {
        Long me = savePreferences("certified-requester", 1, 1, "FEMALE");
        Long candidate = savePreferences("certification-candidate", 1, 1, "FEMALE");
        String semester = SemesterUtils.currentSemester();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        if (!status.equals("MISSING")) {
            LocalDateTime expiresAt = switch (expiry) {
                case "future" -> now.plusHours(1);
                case "past" -> now.minusSeconds(1);
                case "now" -> now;
                default -> null;
            };
            saveMatchingCertification(candidate, term.equals("current") ? semester : "2000-1",
                    CertificationStatus.valueOf(status), expiresAt);
        }
        entityManager.flush();
        entityManager.clear();

        var filtered = preferencesRepository.findNewRecommendationCandidates(
                me, "FEMALE", 1, "[0,0,0,0,0,0,0,0,0]", semester, now, 10);
        var relaxed = preferencesRepository.findNewRecommendationCandidatesIgnoringSmoking(
                me, "FEMALE", "[0,0,0,0,0,0,0,0,0]", semester, now, 10);
        assertThat(filtered).extracting(UserPreferencesRepository.RecommendationCandidate::getUserId)
                .containsExactlyElementsOf(expected ? List.of(candidate) : List.of());
        assertThat(relaxed).extracting(UserPreferencesRepository.RecommendationCandidate::getUserId)
                .containsExactlyElementsOf(expected ? List.of(candidate) : List.of());
        assertThat(certificationRepository.existsApprovedCertification(candidate, semester, now)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @Transactional
    void existingCandidatesRecheckCertificationBeforeRecommendation(boolean requesterIsLow) {
        Long low = savePreferences("existing-low", 1, 1, "FEMALE");
        Long high = savePreferences("existing-high", 1, 1, "FEMALE");
        Long requester = requesterIsLow ? low : high;
        Long receiver = requesterIsLow ? high : low;
        String semester = SemesterUtils.currentSemester();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        saveMatchingCertification(receiver, semester, CertificationStatus.APPROVED, now.plusDays(1));
        entityManager.flush();
        entityManager.clear();
        assertThat(matchRepository.insertCandidateIfAbsent(low, high, low, high, 90.0)).isEqualTo(1);
        Long matchId = matchRepository.findAvailableCandidates(requester, semester, now, PageRequest.of(0, 30))
                .getFirst().getId();

        // 후보 조회 이후 인증이 만료되어도 최종 추천에서 제외
        jdbcTemplate.update("UPDATE certifications SET expires_at = ? WHERE user_id = ?", now, receiver);
        assertThat(matchRepository.findAvailableCandidates(requester, semester, now, PageRequest.of(0, 30))).isEmpty();
        assertThat(matchRepository.recommendCandidateIfAvailable(matchId, requester, semester, now)).isZero();

        jdbcTemplate.update("UPDATE certifications SET expires_at = ? WHERE user_id = ?", now.plusDays(1), receiver);
        assertThat(matchRepository.recommendCandidateIfAvailable(matchId, requester, semester, now)).isEqualTo(1);
        entityManager.clear();
    }

    @Test
    void matchRequestsHasIndexStartingWithUserHighId() {
        // "user_low_id = ? OR user_high_id = ?" 조회에서 user_high_id 쪽도 인덱스를 쓰도록 생성되었는지 확인
        String indexDef = jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'match_requests' AND indexname = ?",
                String.class, "idx_match_requests_high_low");
        assertThat(indexDef).contains("(user_high_id, user_low_id)");
    }

    @Test
    @Transactional
    void insertCandidatesIfAbsentInsertsAllRowsInOneStatementAndSkipsExistingPairs() {
        Long me = savePreferences("batch-me", 1, 1, "FEMALE");
        Long existing = savePreferences("batch-existing", 1, 1, "FEMALE");
        Long fresh = savePreferences("batch-fresh", 1, 1, "FEMALE");
        entityManager.flush();
        entityManager.clear();

        Long existingLow = Math.min(me, existing);
        Long existingHigh = Math.max(me, existing);
        Long freshLow = Math.min(me, fresh);
        Long freshHigh = Math.max(me, fresh);

        // 이미 저장된 쌍
        matchRepository.insertCandidateIfAbsent(existingLow, existingHigh, existingLow, existingHigh, 80.0);

        // 기존 쌍 + 새 쌍을 한 번에 저장하면 새 쌍만 저장됨
        LocalDateTime createdAt = LocalDateTime.of(2026, 3, 2, 10, 30);
        int inserted = matchRepository.insertCandidatesIfAbsent(
                new Long[]{existingLow, freshLow},
                new Long[]{existingHigh, freshHigh},
                new Long[]{existingLow, freshLow},
                new Long[]{existingHigh, freshHigh},
                new Double[]{90.0, 85.5},
                createdAt);
        assertThat(inserted).isEqualTo(1);

        String sql = """
                SELECT match_percentage, user_low_preferences_id, user_high_preferences_id,
                       user_low_status, user_high_status, created_at
                FROM match_requests WHERE user_low_id = ? AND user_high_id = ?
                """;
        var freshRow = jdbcTemplate.queryForMap(sql, freshLow, freshHigh);
        // DB 시각(CURRENT_TIMESTAMP)이 아닌 전달한 Clock 기준 시각으로 저장
        assertThat(((java.sql.Timestamp) freshRow.get("created_at")).toLocalDateTime()).isEqualTo(createdAt);
        assertThat(((Number) freshRow.get("match_percentage")).doubleValue()).isEqualTo(85.5);
        assertThat(((Number) freshRow.get("user_low_preferences_id")).longValue()).isEqualTo(freshLow);
        assertThat(((Number) freshRow.get("user_high_preferences_id")).longValue()).isEqualTo(freshHigh);
        assertThat(freshRow.get("user_low_status")).isEqualTo("NONE");
        assertThat(freshRow.get("user_high_status")).isEqualTo("NONE");

        // 기존 쌍의 점수는 덮어쓰지 않음
        var existingRow = jdbcTemplate.queryForMap(sql, existingLow, existingHigh);
        assertThat(((Number) existingRow.get("match_percentage")).doubleValue()).isEqualTo(80.0);
    }

    @Test
    @Transactional
    void userPreferencesRecalculatesVectorAndSurveyUpdatedAtOnlyWhenSurveyChanges() {
        Long me = savePreferences("pref-me", 1, 1, "FEMALE");
        LocalDateTime past = LocalDateTime.of(2000, 1, 1, 0, 0);
        String staleVector = "[1,1,1,1,1,1,1,1,1]";
        // 시각을 과거로 돌리고, 벡터를 설문과 다른 값으로 바꿔 재계산 여부를 확인
        jdbcTemplate.update("""
                UPDATE user_preferences
                SET updated_at = ?, survey_updated_at = ?, lifestyle_vector = CAST(? AS vector)
                WHERE user_id = ?
                """, past, past, staleVector, me);
        entityManager.clear();

        // 설문과 무관한 변경: updatedAt만 갱신, 벡터와 surveyUpdatedAt은 그대로
        entityManager.find(UserPreferences.class, me).updateIsRerolled(LocalDateTime.now(ZoneId.of("Asia/Seoul")));
        entityManager.flush();
        entityManager.clear();

        var afterReroll = jdbcTemplate.queryForMap("""
                SELECT updated_at, survey_updated_at, lifestyle_vector::text AS vector
                FROM user_preferences WHERE user_id = ?
                """, me);
        assertThat(((java.sql.Timestamp) afterReroll.get("updated_at")).toLocalDateTime()).isAfter(past);
        assertThat(((java.sql.Timestamp) afterReroll.get("survey_updated_at")).toLocalDateTime()).isEqualTo(past);
        assertThat(afterReroll.get("vector")).isEqualTo(staleVector);

        // 설문 수정: 벡터 재계산 및 surveyUpdatedAt 갱신
        SurveyAnswers edited = SurveyAnswers.builder()
                .bedtime(5).snoring(1).sleepTalking(1).organizingStyle(1)
                .temperaturePreference(1).showerFrequency(1).speakerStyle(1)
                .callInRoom(1).eatingInRoom(1).build();
        entityManager.find(UserPreferences.class, me)
                .updatePreferences(1, "intro", edited, List.of(SurveyAnswerField.BEDTIME),
                        LocalDateTime.now(ZoneId.of("Asia/Seoul")));
        entityManager.flush();
        entityManager.clear();

        var afterEdit = jdbcTemplate.queryForMap("""
                SELECT survey_updated_at, lifestyle_vector::text AS vector
                FROM user_preferences WHERE user_id = ?
                """, me);
        assertThat(((java.sql.Timestamp) afterEdit.get("survey_updated_at")).toLocalDateTime()).isAfter(past);
        assertThat(afterEdit.get("vector")).isEqualTo("[1,0,0,0,0,0,0,0,0]");
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
    @Transactional
    void adminCertificationListPaginatesFiltersAndFetchesUserDetailsInOneContentQuery() {
        String semester = "2096-1";
        Long withDetails = savePreferences("admin-list-with-details", 1, 1, "FEMALE");
        Users withoutDetails = Users.builder().oauthId("admin-list-without-details").nickname("admin-list").build();
        entityManager.persist(withoutDetails);

        var olderId = certificationRepository.saveAndFlush(Certification.builder()
                .user(entityManager.getReference(Users.class, withDetails)).semester(semester)
                .imageKey("certifications/first.png").build());
        var newerId = certificationRepository.saveAndFlush(Certification.builder()
                .user(withoutDetails).semester(semester).imageKey("certifications/second.png").build());
        var rejected = certificationRepository.saveAndFlush(Certification.builder()
                .user(withoutDetails).semester("2096-2").imageKey("certifications/third.png")
                .certificationStatus(CertificationStatus.REJECTED).build());
        var sameSemesterRejected = certificationRepository.saveAndFlush(Certification.builder()
                .user(entityManager.getReference(Users.class, savePreferences("admin-list-rejected", 1, 1, "FEMALE")))
                .semester(semester).imageKey("certifications/fourth.png")
                .certificationStatus(CertificationStatus.REJECTED).build());
        jdbcTemplate.update("update certifications set created_at = ? where id in (?, ?)",
                LocalDateTime.of(2026, 1, 1, 0, 0), olderId.getId(), newerId.getId());
        entityManager.clear();

        var statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        boolean previouslyEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            var first = adminCertificationService.getCertifications(CertificationStatus.PENDING, semester, 0, 1);
            assertThat(first.certifications()).singleElement().satisfies(item -> {
                assertThat(item.getCertificationId()).isEqualTo(newerId.getPublicId());
                assertThat(item.getRealName()).isNull();
                assertThat(item.getNickname()).isEqualTo(withoutDetails.getNickname());
                assertThat(item.getImageUrl()).isNull();
            });
            assertThat(first.totalElements()).isEqualTo(2);
            assertThat(first.totalPages()).isEqualTo(2);
            assertThat(first.hasNext()).isTrue();
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);

            entityManager.clear();
            statistics.clear();
            var last = adminCertificationService.getCertifications(CertificationStatus.PENDING, semester, 1, 2);
            assertThat(last.certifications()).isEmpty();
            assertThat(last.totalElements()).isEqualTo(2);
            assertThat(last.hasNext()).isFalse();
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);

            entityManager.clear();
            statistics.clear();
            var allInSemester = adminCertificationService.getCertifications(null, semester, 0, 15);
            assertThat(allInSemester.certifications()).hasSize(3);
            assertThat(allInSemester.totalElements()).isEqualTo(3);
            assertThat(allInSemester.certifications().getFirst().getCertificationId())
                    .isEqualTo(sameSemesterRejected.getPublicId());
            assertThat(allInSemester.certifications().getLast().getCertificationId()).isEqualTo(olderId.getPublicId());
            assertThat(allInSemester.certifications().getLast().getRealName()).isNotBlank();
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);

            var rejectedOnly = adminCertificationService.getCertifications(CertificationStatus.REJECTED, null, 0, 100);
            assertThat(rejectedOnly.certifications()).allSatisfy(item -> assertThat(item.getStatus()).isEqualTo("REJECTED"));
            assertThat(rejectedOnly.certifications()).extracting(item -> item.getCertificationId())
                    .contains(rejected.getPublicId(), sameSemesterRejected.getPublicId());
            var unfiltered = adminCertificationService.getCertifications(null, null, 0, 100);
            assertThat(unfiltered.certifications()).extracting(item -> item.getCertificationId())
                    .contains(olderId.getPublicId(), newerId.getPublicId(), rejected.getPublicId(), sameSemesterRejected.getPublicId());
            verifyNoInteractions(s3Utils);
        } finally {
            statistics.setStatisticsEnabled(previouslyEnabled);
        }
    }

    @Test
    @Transactional
    void adminCertificationDetailReturnsPresignedImageAndRejectingPersistsReview() throws Exception {
        Users applicant = Users.builder().oauthId("admin-review-applicant").nickname("applicant").build();
        Users admin = Users.builder().oauthId("admin-review-admin").nickname("admin").build();
        entityManager.persist(applicant);
        entityManager.persist(admin);
        var certification = certificationRepository.saveAndFlush(Certification.builder()
                .user(applicant).semester(SemesterUtils.currentSemester()).imageKey("certifications/detail.png").build());
        var authentication = authentication(new TestingAuthenticationToken(admin.getId(), null, "ROLE_ADMIN"));
        String path = "/api/admin/certifications/" + certification.getPublicId();
        String url = "https://example.com/image?signature=test";
        when(s3Utils.createDownloadUrl(certification.getImageKey())).thenReturn(url);

        mockMvc.perform(get(path).with(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl").value(url));

        String request = """
                {"expectedVersion":0,"adminComment":"%s"}
                """.formatted("가".repeat(500));
        mockMvc.perform(patch(path + "/reject").with(authentication).contentType("application/json").content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.adminComment").value("가".repeat(500)))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.reviewedAt").exists())
                .andExpect(jsonPath("$.data.imageUrl").doesNotExist());

        entityManager.clear();
        var saved = certificationRepository.findByPublicId(certification.getPublicId()).orElseThrow();
        assertThat(saved.getReviewedBy().getId()).isEqualTo(admin.getId());
        assertThat(saved.getReviewedAt()).isNotNull();
        assertThat(saved.getAdminComment()).isEqualTo("가".repeat(500));
        assertThat(saved.getExpiresAt()).isNull();

        mockMvc.perform(patch(path + "/reject").with(authentication).contentType("application/json").content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CERTIFICATION_ALREADY_PROCESSED"));
    }

    @Test
    void concurrentAdminReviewReturnsConflictAfterAnotherReviewCommits() throws Exception {
        CertificationFixture fixture = saveCertificationFixture(CertificationStatus.PENDING, SemesterUtils.currentSemester());
        var transactions = new TransactionTemplate(transactionManager);
        Long adminId = transactions.execute(transaction -> {
            Users admin = Users.builder().oauthId("admin-concurrent-review").nickname("admin").build();
            ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);
            entityManager.persist(admin);
            return admin.getId();
        });
        var authentication = authentication(new TestingAuthenticationToken(adminId, null, "ROLE_ADMIN"));
        String path = "/api/admin/certifications/" + fixture.publicId();
        var competingTransaction = new TransactionTemplate(transactionManager);
        competingTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        try {
            // 승인과 거절이 같은 버전을 읽도록 두 트랜잭션의 실행 순서를 제어한다.
            transactions.executeWithoutResult(rejection -> {
                var pending = certificationRepository.findByPublicId(fixture.publicId()).orElseThrow();
                assertThat(pending.getVersion()).isZero();
                competingTransaction.executeWithoutResult(approval -> {
                    var request = new AdminCertificationApproveRequestDto();
                    ReflectionTestUtils.setField(request, "expectedVersion", 0L);
                    var approved = adminCertificationService.approveCertification(adminId, fixture.publicId(), request);
                    assertThat(approved.getStatus()).isEqualTo("APPROVED");
                    assertThat(approved.getVersion()).isEqualTo(1L);
                });
                try {
                    mockMvc.perform(patch(path + "/reject").with(authentication).contentType("application/json")
                                    .content("{\"expectedVersion\":0,\"adminComment\":\"동시 거절\"}"))
                            .andExpect(status().isConflict())
                            .andExpect(jsonPath("$.code").value("UPDATE_CONFLICT"));
                } catch (Exception e) {
                    throw new AssertionError(e);
                } finally {
                    rejection.setRollbackOnly();
                }
            });

            transactions.executeWithoutResult(transaction -> {
                var saved = certificationRepository.findByPublicId(fixture.publicId()).orElseThrow();
                assertThat(saved.getCertificationStatus()).isEqualTo(CertificationStatus.APPROVED);
                assertThat(saved.getVersion()).isEqualTo(1L);
                assertThat(saved.getReviewedBy().getId()).isEqualTo(adminId);
                assertThat(saved.getReviewedAt()).isNotNull();
            });
            mockMvc.perform(patch(path + "/approve").with(authentication).contentType("application/json")
                            .content("{\"expectedVersion\":0}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CERTIFICATION_ALREADY_PROCESSED"));
        } finally {
            deleteCertificationFixture(fixture.userId());
            jdbcTemplate.update("delete from users where id = ?", adminId);
        }
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

    @Test
    void matchingLockWaitTimesOutAndRollsBack() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Long[] fixture = tx.execute(status -> {
            String suffix = UUID.randomUUID().toString();
            Long low = savePreferences("lock-low-" + suffix, 1, 1, "FEMALE");
            Long high = savePreferences("lock-high-" + suffix, 1, 1, "FEMALE");
            saveMatchingCertification(high, SemesterUtils.currentSemester(), CertificationStatus.APPROVED,
                    LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(1));
            matchRepository.insertCandidateIfAbsent(low, high, low, high, 90.0);
            MatchRequests pair = matchRepository.findByIdsWithoutLock(low, high).orElseThrow();
            pair.recommend(low, LocalDateTime.now(ZoneId.of("Asia/Seoul")));
            entityManager.flush();
            return new Long[]{low, high, pair.getId()};
        });
        String originalTimeout = tx.execute(status ->
                (String) entityManager.createNativeQuery("SHOW lock_timeout").getSingleResult());
        EntityManager holder = entityManagerFactory.createEntityManager();
        var executor = Executors.newSingleThreadExecutor();
        try {
            holder.getTransaction().begin();
            holder.find(MatchRequests.class, fixture[2], LockModeType.PESSIMISTIC_WRITE);

            var request = executor.submit(() -> {
                long startedAt = System.nanoTime();
                assertThrows(PessimisticLockingFailureException.class,
                        () -> matchingService.heart(fixture[0], fixture[1]));
                return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            });
            assertThat(request.get(5, TimeUnit.SECONDS)).isBetween(1200L, 5000L);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT user_low_status FROM match_requests WHERE match_request_id = ?",
                    String.class, fixture[2])).isEqualTo("RECOMMENDED");
            String restoredTimeout = tx.execute(status ->
                    (String) entityManager.createNativeQuery("SHOW lock_timeout").getSingleResult());
            assertThat(restoredTimeout).isEqualTo(originalTimeout);
        } finally {
            if (holder.getTransaction().isActive()) holder.getTransaction().rollback();
            holder.close();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            tx.executeWithoutResult(status -> {
                jdbcTemplate.update("DELETE FROM match_requests WHERE match_request_id = ?", fixture[2]);
                for (String table : List.of("certifications", "user_preferences", "user_details", "users")) {
                    String column = table.equals("users") ? "id" : "user_id";
                    jdbcTemplate.update("DELETE FROM " + table + " WHERE " + column + " IN (?, ?)", fixture[0], fixture[1]);
                }
            });
        }
    }

    // 바이트코드 enhancement(build.gradle의 org.hibernate.orm 플러그인) 회귀 테스트
    // enhancement가 빠지면 Users의 mappedBy @OneToOne(userDetails, userPreferences)이
    // 다시 즉시 로딩되어 Users 단건 조회에 쿼리가 3번 나가므로 이 테스트가 실패함
    // (기능은 그대로 동작해서 다른 테스트로는 드러나지 않음)
    @Test
    @Transactional
    void usersInverseOneToOneIsLazyWithBytecodeEnhancement() {
        Long userId = savePreferences("enhancement-guard", 1, 1, "FEMALE");
        entityManager.flush();
        entityManager.clear();

        var statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        boolean previouslyEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            Users user = entityManager.find(Users.class, userId);

            // Users 조회는 users 테이블 1번만
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
            assertThat(org.hibernate.Hibernate.isPropertyInitialized(user, "userDetails")).isFalse();
            assertThat(org.hibernate.Hibernate.isPropertyInitialized(user, "userPreferences")).isFalse();

            // 실제로 접근할 때 조회(같은 lazy group이라 설문과 상세 정보를 한 번에 가져옴)
            assertThat(user.getUserPreferences().getIsCompleted()).isTrue();
            assertThat(user.getUserDetails().getGender()).isEqualTo("FEMALE");
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        } finally {
            statistics.setStatisticsEnabled(previouslyEnabled);
        }
    }

    @Test
    void closeOtherRequestsOfBothUsersInOneQueryAndWaitsForLockedRow() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Long[] users = tx.execute(status -> {
            String suffix = UUID.randomUUID().toString();
            return new Long[]{
                    savePreferences("close-a-" + suffix, 1, 1, "FEMALE"),
                    savePreferences("close-b-" + suffix, 1, 1, "FEMALE"),
                    savePreferences("close-c-" + suffix, 1, 1, "FEMALE"),
                    savePreferences("close-d-" + suffix, 1, 1, "FEMALE")};
        });
        Long a = users[0], b = users[1], c = users[2], d = users[3];
        // (A,B) 확정 행, (A,C)·(B,D) 닫을 행, (C,D) 무관한 행
        Long[][] pairs = {{a, b}, {a, c}, {b, d}, {c, d}};
        Long[] ids = tx.execute(status -> {
            Long[] result = new Long[pairs.length];
            for (int i = 0; i < pairs.length; i++) {
                Long low = Math.min(pairs[i][0], pairs[i][1]);
                Long high = Math.max(pairs[i][0], pairs[i][1]);
                matchRepository.insertCandidateIfAbsent(low, high, low, high, 90.0);
                result[i] = matchRepository.findByIdsWithoutLock(low, high).orElseThrow().getId();
            }
            return result;
        });
        Long confirmedId = ids[0], acId = ids[1], bdId = ids[2], cdId = ids[3];

        EntityManager holder = entityManagerFactory.createEntityManager();
        var executor = Executors.newSingleThreadExecutor();
        try {
            // 다른 트랜잭션이 닫을 행 하나를 잠근 상태
            holder.getTransaction().begin();
            holder.find(MatchRequests.class, acId, LockModeType.PESSIMISTIC_WRITE);

            var closing = executor.submit(() -> tx.execute(status ->
                    matchRepository.closeOtherMatchRequestsForUsers(a, b, confirmedId)));

            // 건너뛰지 않고 잠금이 풀릴 때까지 기다림
            Thread.sleep(500);
            assertThat(closing.isDone()).isFalse();

            holder.getTransaction().rollback();
            assertThat(closing.get(5, TimeUnit.SECONDS)).isEqualTo(2);

            // 각 행에서 A·B 쪽 칸만 CLOSED, 확정 행과 무관한 행은 그대로
            assertThat(sideStatus(acId, a)).isEqualTo("CLOSED");
            assertThat(sideStatus(acId, c)).isEqualTo("NONE");
            assertThat(sideStatus(bdId, b)).isEqualTo("CLOSED");
            assertThat(sideStatus(bdId, d)).isEqualTo("NONE");
            assertThat(sideStatus(confirmedId, a)).isEqualTo("NONE");
            assertThat(sideStatus(confirmedId, b)).isEqualTo("NONE");
            assertThat(sideStatus(cdId, c)).isEqualTo("NONE");
            assertThat(sideStatus(cdId, d)).isEqualTo("NONE");

            // 이미 CLOSED인 쪽은 다시 대상이 되지 않음
            Integer closedAgain = tx.execute(status ->
                    matchRepository.closeOtherMatchRequestsForUsers(a, b, confirmedId));
            assertThat(closedAgain).isZero();
        } finally {
            if (holder.getTransaction().isActive()) holder.getTransaction().rollback();
            holder.close();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            tx.executeWithoutResult(status -> {
                for (Long id : ids) {
                    jdbcTemplate.update("DELETE FROM match_requests WHERE match_request_id = ?", id);
                }
                for (String table : List.of("certifications", "user_preferences", "user_details", "users")) {
                    String column = table.equals("users") ? "id" : "user_id";
                    jdbcTemplate.update("DELETE FROM " + table + " WHERE " + column + " IN (?, ?, ?, ?)", a, b, c, d);
                }
            });
        }
    }

    @Test
    void banCloseWaitsForUserPreferencesLockThenClosesRequestsBeforeChatRooms() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Long[] users = tx.execute(status -> {
            String suffix = UUID.randomUUID().toString();
            return new Long[]{
                    savePreferences("ban-a-" + suffix, 1, 1, "FEMALE"),
                    savePreferences("ban-b-" + suffix, 1, 1, "FEMALE"),
                    savePreferences("ban-c-" + suffix, 1, 1, "FEMALE"),
                    savePreferences("ban-d-" + suffix, 1, 1, "FEMALE")};
        });
        Long a = users[0], b = users[1], c = users[2], d = users[3];
        Long[] ids = tx.execute(status -> {
            // (A,B) 양쪽 최종 확정, (A,C) 하트 매칭 + 채팅방, (A,D) A 쪽 이미 CLOSED, (C,D) 무관
            Long ab = saveHeartMatchWithRoom(Math.min(a, b), Math.max(a, b));
            Long ac = saveHeartMatchWithRoom(Math.min(a, c), Math.max(a, c));
            matchRepository.insertCandidateIfAbsent(Math.min(a, d), Math.max(a, d), Math.min(a, d), Math.max(a, d), 90.0);
            matchRepository.insertCandidateIfAbsent(Math.min(c, d), Math.max(c, d), Math.min(c, d), Math.max(c, d), 90.0);
            Long ad = matchRepository.findByIdsWithoutLock(a, d).orElseThrow().getId();
            Long cd = matchRepository.findByIdsWithoutLock(c, d).orElseThrow().getId();
            jdbcTemplate.update("UPDATE match_requests SET user_low_status = 'FINAL_CONFIRMED', "
                    + "user_high_status = 'FINAL_CONFIRMED' WHERE match_request_id = ?", ab);
            jdbcTemplate.update("UPDATE match_requests SET "
                    + "user_low_status = CASE WHEN user_low_id = ? THEN 'CLOSED' ELSE user_low_status END, "
                    + "user_high_status = CASE WHEN user_high_id = ? THEN 'CLOSED' ELSE user_high_status END "
                    + "WHERE match_request_id = ?", a, a, ad);
            return new Long[]{ab, ac, ad, cd};
        });
        Long abId = ids[0], acId = ids[1], adId = ids[2], cdId = ids[3];

        EntityManager holder = entityManagerFactory.createEntityManager();
        var executor = Executors.newSingleThreadExecutor();
        try {
            // A가 포함된 확정 처리 등이 A의 설문 행을 잡고 있는 상태
            holder.getTransaction().begin();
            holder.find(UserPreferences.class, a, LockModeType.PESSIMISTIC_WRITE);

            var closing = executor.submit(() -> matchingService.closeAllMatchRequestsByUserId(a));

            // NOWAIT으로 바로 실패하지 않고, 설문 행 잠금이 풀릴 때까지 기다림
            Thread.sleep(500);
            assertThat(closing.isDone()).isFalse();
            assertThat(sideStatus(acId, a)).isEqualTo("HEART");

            holder.getTransaction().rollback();
            closing.get(5, TimeUnit.SECONDS);

            // 미확정 요청은 A 쪽만 CLOSED, 양쪽 확정 건과 무관한 행은 그대로
            assertThat(sideStatus(acId, a)).isEqualTo("CLOSED");
            assertThat(sideStatus(acId, c)).isEqualTo("HEART");
            assertThat(sideStatus(adId, a)).isEqualTo("CLOSED");
            assertThat(sideStatus(abId, a)).isEqualTo("FINAL_CONFIRMED");
            assertThat(sideStatus(abId, b)).isEqualTo("FINAL_CONFIRMED");
            assertThat(sideStatus(cdId, c)).isEqualTo("NONE");
            assertThat(sideStatus(cdId, d)).isEqualTo("NONE");
            // 미확정 요청의 채팅방도 닫힘 (확정 쌍의 채팅방 처리는 추후 논의 대상이라 여기서 검증하지 않음)
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM chat_rooms WHERE match_request_id = ?", String.class, acId))
                    .isEqualTo("CLOSED");
        } finally {
            if (holder.getTransaction().isActive()) holder.getTransaction().rollback();
            holder.close();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            tx.executeWithoutResult(status -> {
                for (Long id : ids) {
                    jdbcTemplate.update("DELETE FROM chat_rooms WHERE match_request_id = ?", id);
                    jdbcTemplate.update("DELETE FROM match_requests WHERE match_request_id = ?", id);
                }
                for (String table : List.of("certifications", "user_preferences", "user_details", "users")) {
                    String column = table.equals("users") ? "id" : "user_id";
                    jdbcTemplate.update("DELETE FROM " + table + " WHERE " + column + " IN (?, ?, ?, ?)", a, b, c, d);
                }
            });
        }
    }

    // match_requests 행에서 특정 사용자 쪽 상태 조회
    private String sideStatus(Long matchRequestId, Long userId) {
        return jdbcTemplate.queryForObject("""
                SELECT CASE WHEN user_low_id = ? THEN user_low_status ELSE user_high_status END
                FROM match_requests WHERE match_request_id = ?
                """, String.class, userId, matchRequestId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"receiver", "requester"})
    @Transactional
    void heartIsRejectedWhenEitherUserIsAlreadyConfirmed(String confirmedSide) {
        Long requester = savePreferences("heart-req-" + confirmedSide, 1, 1, "FEMALE");
        Long receiver = savePreferences("heart-rcv-" + confirmedSide, 1, 1, "FEMALE");
        String semester = SemesterUtils.currentSemester();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        saveMatchingCertification(receiver, semester, CertificationStatus.APPROVED, now.plusDays(1));
        Long low = Math.min(requester, receiver);
        Long high = Math.max(requester, receiver);
        // 확정 처리와 추천이 겹쳐 CLOSED되지 않고 남은 행: 요청자에게 추천된 상태
        matchRepository.insertCandidateIfAbsent(low, high, low, high, 90.0);
        Long matchId = matchRepository.findByIdsWithoutLock(low, high).orElseThrow().getId();
        assertThat(matchRepository.recommendCandidateIfAvailable(matchId, requester, semester, now)).isEqualTo(1);

        // 한쪽이 이미 다른 사람과 최종 확정된 상태
        Long confirmed = confirmedSide.equals("receiver") ? receiver : requester;
        jdbcTemplate.update("UPDATE user_preferences SET is_matched = true WHERE user_id = ?", confirmed);
        entityManager.clear();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> matchingService.heart(requester, receiver));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_HEARTABLE_STATUS);
        String statusColumn = requester.equals(low) ? "user_low_status" : "user_high_status";
        assertThat(jdbcTemplate.queryForObject(
                "SELECT " + statusColumn + " FROM match_requests WHERE match_request_id = ?",
                String.class, matchId)).isEqualTo("RECOMMENDED");

        // 둘 다 확정 전이면 정상 처리
        jdbcTemplate.update("UPDATE user_preferences SET is_matched = false WHERE user_id = ?", confirmed);
        entityManager.clear();
        matchingService.heart(requester, receiver);
        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT " + statusColumn + " FROM match_requests WHERE match_request_id = ?",
                String.class, matchId)).isEqualTo("HEART");
    }

    @Test
    void recommendSkipsRowLockedByAnotherTransactionWithoutWaiting() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String semester = SemesterUtils.currentSemester();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        Long[] fixture = tx.execute(status -> {
            String suffix = UUID.randomUUID().toString();
            Long low = savePreferences("skip-low-" + suffix, 1, 1, "FEMALE");
            Long high = savePreferences("skip-high-" + suffix, 1, 1, "FEMALE");
            saveMatchingCertification(high, semester, CertificationStatus.APPROVED, now.plusDays(1));
            matchRepository.insertCandidateIfAbsent(low, high, low, high, 90.0);
            return new Long[]{low, high, matchRepository.findByIdsWithoutLock(low, high).orElseThrow().getId()};
        });
        EntityManager holder = entityManagerFactory.createEntityManager();
        var executor = Executors.newSingleThreadExecutor();
        try {
            // 다른 트랜잭션(예: 확정 처리)이 추천 대상 행을 잠근 상태
            holder.getTransaction().begin();
            holder.find(MatchRequests.class, fixture[2], LockModeType.PESSIMISTIC_WRITE);

            // 잠긴 행은 기다리지 않고 0을 반환(lock_timeout 미설정이므로 기다렸다면 5초 제한에 걸림)
            var skipped = executor.submit(() -> {
                long startedAt = System.nanoTime();
                Integer updated = tx.execute(status ->
                        matchRepository.recommendCandidateIfAvailable(fixture[2], fixture[0], semester, now));
                return new long[]{updated, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)};
            }).get(5, TimeUnit.SECONDS);
            assertThat(skipped[0]).isZero();
            assertThat(skipped[1]).isLessThan(1000L);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT user_low_status FROM match_requests WHERE match_request_id = ?",
                    String.class, fixture[2])).isEqualTo("NONE");

            // 잠금이 풀리면 정상 추천
            holder.getTransaction().rollback();
            Integer updated = tx.execute(status ->
                    matchRepository.recommendCandidateIfAvailable(fixture[2], fixture[0], semester, now));
            assertThat(updated).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT user_low_status FROM match_requests WHERE match_request_id = ?",
                    String.class, fixture[2])).isEqualTo("RECOMMENDED");
        } finally {
            if (holder.getTransaction().isActive()) holder.getTransaction().rollback();
            holder.close();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            tx.executeWithoutResult(status -> {
                jdbcTemplate.update("DELETE FROM match_requests WHERE match_request_id = ?", fixture[2]);
                for (String table : List.of("certifications", "user_preferences", "user_details", "users")) {
                    String column = table.equals("users") ? "id" : "user_id";
                    jdbcTemplate.update("DELETE FROM " + table + " WHERE " + column + " IN (?, ?)", fixture[0], fixture[1]);
                }
            });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"MALE", "FEMALE"})
    @Transactional
    void userDetailsRejectGenderInUpdate(String gender) throws Exception {
        Long userId = savePreferences("immutable-gender", 1, 1, "FEMALE");

        mockMvc.perform(patch("/api/users/details")
                        .with(authentication(new TestingAuthenticationToken(userId, null, "ROLE_USER")))
                        .contentType("application/json")
                        .content("{\"gender\":\"" + gender + "\",\"age\":22}"))
                .andExpect(status().isBadRequest());

        assertThat(jdbcTemplate.queryForObject("SELECT gender FROM user_details WHERE user_id = ?",
                String.class, userId)).isEqualTo("FEMALE");
        assertThat(jdbcTemplate.queryForObject("SELECT age FROM user_details WHERE user_id = ?",
                Integer.class, userId)).isEqualTo(21);
    }

    @Test
    @Transactional
    void userDetailsAllowUpdatingOtherFieldsWithoutChangingGender() throws Exception {
        Long userId = savePreferences("editable-age", 1, 1, "FEMALE");

        mockMvc.perform(patch("/api/users/details")
                        .with(authentication(new TestingAuthenticationToken(userId, null, "ROLE_USER")))
                        .contentType("application/json")
                        .content("{\"age\":22}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.age").value(22))
                .andExpect(jsonPath("$.data.gender").value("FEMALE"));

        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject("SELECT age FROM user_details WHERE user_id = ?",
                Integer.class, userId)).isEqualTo(22);
        assertThat(jdbcTemplate.queryForObject("SELECT gender FROM user_details WHERE user_id = ?",
                String.class, userId)).isEqualTo("FEMALE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"MALE", "FEMALE"})
    @Transactional
    void userDetailsAllowGenderOnInitialRegistration(String gender) throws Exception {
        Users user = Users.builder().oauthId("initial-gender").nickname("initial-gender").build();
        entityManager.persist(user);
        entityManager.flush();
        privacyConsentService.submitOnboarding(user.getId(), true, "1.0", true, "1.0", false, "1.0");

        mockMvc.perform(post("/api/users/details")
                        .with(authentication(new TestingAuthenticationToken(user.getId(), null, "ROLE_USER")))
                        .contentType("application/json")
                        .content("""
                                {"realName":"등록 사용자","studentId":"20260001","age":21,
                                 "gender":"%s","department":"test","phoneNumber":"010-1234-5678"}
                                """.formatted(gender)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.gender").value(gender));

        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject("SELECT gender FROM user_details WHERE user_id = ?",
                String.class, user.getId())).isEqualTo(gender);
    }

    @ParameterizedTest
    @CsvSource({
            "/api/admin/users, USER, 403",
            "/api/admin/certifications, USER, 403",
            "/api/admin/monitoring/summary, USER, 403",
            "/api/admin/match/config, USER, 403",
            "/api/matching/status, ADMIN, 403",
            "/api/admin/users, ADMIN, 200",
            "/api/users/blocks, USER, 200",
            "/api/users/blocks, ADMIN, 200"
    })
    @Transactional
    void securityEnforcesRolesWithoutAuthenticationAspect(String path, UserRole role, int expectedStatus) throws Exception {
        Users user = Users.builder().oauthId("security-role").nickname("security-role").build();
        ReflectionTestUtils.setField(user, "role", role);
        entityManager.persist(user);
        entityManager.flush();
        String token = jwtTokenProvider.createAccessToken(hashIdsUtils.encode(user.getId()), role.name(), "test-session");

        var result = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().is(expectedStatus));
        if (expectedStatus == 403) {
            result.andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.message").value(ErrorCode.FORBIDDEN.getMessage()))
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andExpect(jsonPath("$.trace").doesNotExist());
        }
    }

    @ParameterizedTest
    @MethodSource("invalidJsonRequests")
    void invalidJsonReturnsCommonBadRequest(String path, String body) throws Exception {
        String authority = path.startsWith("/api/admin/") ? "ROLE_ADMIN" : "ROLE_USER";
        mockMvc.perform(patch(path)
                        .with(authentication(new TestingAuthenticationToken(1L, null, authority)))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("요청 값이나 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    private static Stream<Arguments> invalidJsonRequests() {
        String approval = "/api/admin/certifications/123e4567-e89b-12d3-a456-426614174000/approve";
        return Stream.of(
                Arguments.of("/api/matching/requests", "{"),
                Arguments.of("/api/matching/requests", "{\"receiverId\":{},\"matchStatus\":\"HEART\"}"),
                Arguments.of("/api/matching/requests", ""),
                Arguments.of("/api/users/details", "{"),
                Arguments.of("/api/users/details", "{\"age\":{}}"),
                Arguments.of("/api/surveys/me", "{"),
                Arguments.of(approval, "{"),
                Arguments.of(approval, "{\"expectedVersion\":{}}")
        );
    }

    @Test
    void noticeIngestJsonFormatErrorUsesCommonCode() throws Exception {
        mockMvc.perform(post("/internal/notices")
                        .header("X-Ingest-Key", "test-ingest-key")
                        .contentType("application/json")
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("요청 값이나 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void noticePaginationErrorUsesCommonCodeAndKeepsCustomMessage() throws Exception {
        mockMvc.perform(get("/api/notices").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("page must be nonnegative and size must be between 1 and 100"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/users", "/api/matching/status", "/api/users/blocks"})
    void protectedEndpointsStillRequireLoginWithoutAuthenticationAspect(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.getMessage()));
    }

    @Test
    void invalidUserIdentifierInJwtReturnsCommonUnauthorizedResponse() throws Exception {
        String token = jwtTokenProvider.createAccessToken("!", "USER", "test-session");

        mockMvc.perform(get("/api/matching/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.getMessage()));
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"BANNED", "WITHDRAWN"})
    @Transactional
    void jwtFilterStillRejectsUnavailableUsersWithoutAuthenticationAspect(UserStatus userStatus) throws Exception {
        Users user = Users.builder().oauthId("unavailable-user").nickname("unavailable-user").build();
        if (userStatus == UserStatus.BANNED) user.ban();
        else user.withdraw();
        entityManager.persist(user);
        entityManager.flush();
        String token = jwtTokenProvider.createAccessToken(hashIdsUtils.encode(user.getId()), "USER", "test-session");

        mockMvc.perform(get("/api/users/blocks").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @CsvSource({"GET, missing", "PATCH, missing", "GET, incomplete", "PATCH, incomplete"})
    @Transactional
    void surveyEndpointsKeepSurveyRequiredResponseWithoutSurveyAspect(String method, String state) throws Exception {
        Long userId = savePreferences("survey-service-guard", 1, 0, "FEMALE");
        if (state.equals("missing")) preferencesRepository.deleteById(userId);
        else jdbcTemplate.update("UPDATE user_preferences SET is_completed = false WHERE user_id = ?", userId);
        entityManager.flush();
        entityManager.clear();
        String token = jwtTokenProvider.createAccessToken(hashIdsUtils.encode(userId), "USER", "test-session");
        var request = method.equals("GET") ? get("/api/surveys/me") : patch("/api/surveys/me")
                .contentType("application/json")
                .content("""
                        {"smokingStatus":0,"visibleProfileFields":["BEDTIME"],
                         "answers":{"bedtime":1,"snoring":1,"sleepTalking":1,"organizingStyle":1,
                                    "temperaturePreference":1,"showerFrequency":1,"speakerStyle":1,
                                    "callInRoom":1,"eatingInRoom":1}}
                        """);

        mockMvc.perform(request.header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(ErrorCode.SURVEY_REQUIRED.getMessage()));
    }

    @Test
    void confirmationAndOtherRejectionCompleteWithoutDeadlock() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Long[] fixture = tx.execute(status -> {
            String suffix = UUID.randomUUID().toString();
            Long a = savePreferences("confirm-a-" + suffix, 1, 0, "FEMALE");
            Long b = savePreferences("confirm-b-" + suffix, 1, 0, "FEMALE");
            Long c = savePreferences("reject-c-" + suffix, 1, 0, "FEMALE");
            LocalDateTime expiresAt = LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(1);
            saveMatchingCertification(a, SemesterUtils.currentSemester(), CertificationStatus.APPROVED, expiresAt);
            saveMatchingCertification(b, SemesterUtils.currentSemester(), CertificationStatus.APPROVED, expiresAt);
            return new Long[]{a, b, c, saveHeartMatchWithRoom(a, b), saveHeartMatchWithRoom(a, c)};
        });
        Long a = fixture[0], b = fixture[1], c = fixture[2], pairAB = fixture[3], pairAC = fixture[4];
        CountDownLatch rejectionHasMatch = new CountDownLatch(1);
        CountDownLatch allowRejection = new CountDownLatch(1);
        CountDownLatch confirmationStarted = new CountDownLatch(1);
        AtomicInteger confirmationPid = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(2);
        try {
            matchingService.confirm(a, b);

            // 거절 대상 관계를 먼저 잠근 상태에서 다른 상대와 최종 확정
            var rejection = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbcTemplate.queryForObject("""
                        SELECT match_request_id FROM match_requests
                        WHERE match_request_id = ? FOR NO KEY UPDATE
                        """, Long.class, pairAC);
                rejectionHasMatch.countDown();
                awaitMatchingLatch(allowRejection);
                matchingService.reject(c, a);
            }));
            assertTrue(rejectionHasMatch.await(5, TimeUnit.SECONDS));
            var confirmation = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                confirmationPid.set(jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class));
                confirmationStarted.countDown();
                matchingService.confirm(b, a);
            }));
            assertTrue(confirmationStarted.await(5, TimeUnit.SECONDS));

            // 확정이 거절 대상 관계의 잠금을 기다릴 때 거절을 진행
            boolean waitingForLock = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (System.nanoTime() < deadline) {
                waitingForLock = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                        "SELECT wait_event_type = 'Lock' FROM pg_stat_activity WHERE pid = ?",
                        Boolean.class, confirmationPid.get()));
                if (waitingForLock) break;
                if (confirmation.isDone()) confirmation.get();
                Thread.sleep(10);
            }
            assertTrue(waitingForLock, "확정이 관계 잠금 대기에 진입해야 합니다.");
            allowRejection.countDown();
            rejection.get(5, TimeUnit.SECONDS);
            confirmation.get(5, TimeUnit.SECONDS);

            assertThat(jdbcTemplate.queryForMap("""
                    SELECT user_low_status, user_high_status FROM match_requests WHERE match_request_id = ?
                    """, pairAB)).containsEntry("user_low_status", "FINAL_CONFIRMED")
                    .containsEntry("user_high_status", "FINAL_CONFIRMED");
            assertThat(jdbcTemplate.queryForMap("""
                    SELECT user_low_status, user_high_status FROM match_requests WHERE match_request_id = ?
                    """, pairAC)).containsEntry("user_low_status", "CLOSED")
                    .containsEntry("user_high_status", "REJECTED");
            for (Long userId : List.of(a, b)) {
                assertThat(jdbcTemplate.queryForObject("SELECT is_matched FROM user_preferences WHERE user_id = ?",
                        Boolean.class, userId)).isTrue();
            }
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM chat_rooms WHERE match_request_id = ?",
                    String.class, pairAB)).isEqualTo("OPEN");
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM chat_rooms WHERE match_request_id = ?",
                    String.class, pairAC)).isEqualTo("CLOSED");
        } finally {
            allowRejection.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            tx.executeWithoutResult(status -> {
                jdbcTemplate.update("DELETE FROM chat_rooms WHERE match_request_id IN (?, ?)", pairAB, pairAC);
                jdbcTemplate.update("DELETE FROM match_requests WHERE match_request_id IN (?, ?)", pairAB, pairAC);
                for (String table : List.of("certifications", "user_preferences", "user_details", "users")) {
                    String column = table.equals("users") ? "id" : "user_id";
                    jdbcTemplate.update("DELETE FROM " + table + " WHERE " + column + " IN (?, ?, ?)", a, b, c);
                }
            });
        }
    }

    private void awaitMatchingLatch(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("매칭 동시 실행 대기 시간 초과");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private Long saveHeartMatchWithRoom(Long low, Long high) {
        MatchRequests pair = MatchRequests.builder()
                .userLow(entityManager.getReference(Users.class, low))
                .userHigh(entityManager.getReference(Users.class, high))
                .userLowPreferences(entityManager.getReference(UserPreferences.class, low))
                .userHighPreferences(entityManager.getReference(UserPreferences.class, high))
                .userLowStatus(MatchStatus.HEART).userHighStatus(MatchStatus.HEART).matchPercentage(90.0).build();
        entityManager.persist(pair);
        entityManager.persist(ChatRoom.forMatch(pair));
        entityManager.flush();
        return pair.getId();
    }

    private void saveApprovedMatchingCertifications(Long... userIds) {
        String semester = SemesterUtils.currentSemester();
        LocalDateTime expiresAt = LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(1);
        for (Long userId : userIds) {
            saveMatchingCertification(userId, semester, CertificationStatus.APPROVED, expiresAt);
        }
    }

    private void saveMatchingCertification(Long userId, String semester, CertificationStatus status, LocalDateTime expiresAt) {
        Certification certification = Certification.builder()
                .user(entityManager.getReference(Users.class, userId))
                .semester(semester).imageKey("certifications/matching-test.jpg")
                .certificationStatus(status).build();
        if (status == CertificationStatus.APPROVED) {
            certification.approve(null, null, expiresAt);
        }
        entityManager.persist(certification);
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
