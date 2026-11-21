package com.irummate.domain.recruitment.service;

import com.irummate.domain.chat.service.RecruitmentChatRoomService;

import com.irummate.domain.recruitment.dto.RecruitmentParticipantRemoveRequestDto;
import com.irummate.domain.recruitment.entity.*;
import com.irummate.domain.recruitment.repository.RecruitmentParticipantsRepository;
import com.irummate.domain.recruitment.repository.RecruitmentsRepository;
import com.irummate.domain.recruitment.repository.RecruitmentBookmarksRepository;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.util.HashIdsUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecruitmentParticipantsServiceTest {
    private final RecruitmentsRepository recruitments = mock(RecruitmentsRepository.class);
    private final RecruitmentParticipantsRepository participants = mock(RecruitmentParticipantsRepository.class);
    private final HashIdsUtils hashIds = new HashIdsUtils("recruitment-participants-test", "8");
    private final RecruitmentParticipationService service = new RecruitmentParticipationService(
            recruitments, participants, mock(UsersRepository.class), hashIds, mock(RecruitmentBookmarksRepository.class), Clock.system(ZoneId.of("Asia/Seoul")), mock(RecruitmentChatRoomService.class));
    private final LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    private final Users author = user(1L);
    private final Users member = user(2L);

    @BeforeEach
    void setUp() {
        EntityManager entityManager = mock(EntityManager.class);
        when(entityManager.createNativeQuery("SET LOCAL lock_timeout = '1500ms'"))
                .thenReturn(mock(Query.class));
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
    }

    @Test
    void listUsesPublicIdsAndMarksViewer() {
        Recruitments recruitment = recruitment();
        when(recruitments.findById(11L)).thenReturn(Optional.of(recruitment));
        var participantRows = List.of(
                row(1L, RecruitmentParticipantRole.HOST, now.minusHours(2)),
                row(2L, RecruitmentParticipantRole.MEMBER, now.minusHours(1)));
        when(participants.findParticipantRows(11L, RecruitmentParticipantStatus.JOINED)).thenReturn(participantRows);

        var response = service.getRecruitmentParticipants(2L, 11L);

        assertEquals(11L, response.getRecruitmentId());
        assertEquals(2, response.getNowParticipants());
        assertEquals(3, response.getMaxParticipants());
        assertEquals(List.of(hashIds.encode(1L), hashIds.encode(2L)),
                response.getParticipants().stream().map(participant -> participant.getUserId()).toList());
        assertFalse(response.getParticipants().getFirst().isMe());
        assertTrue(response.getParticipants().getLast().isMe());
        assertEquals(RecruitmentParticipantRole.HOST, response.getParticipants().getFirst().getRole());
        assertEquals("사용자2", response.getParticipants().getLast().getNickname());
        assertEquals("https://example.com/2.png", response.getParticipants().getLast().getProfileImageUrl());
        assertEquals(now.minusHours(1), response.getParticipants().getLast().getJoinedAt());
        verify(participants).findParticipantRows(11L, RecruitmentParticipantStatus.JOINED);
    }

    @Test
    void nonAuthorCannotReadHiddenPostParticipants() {
        Recruitments recruitment = recruitment();
        ReflectionTestUtils.setField(recruitment, "visibility", RecruitmentVisibility.HIDDEN);
        when(recruitments.findById(11L)).thenReturn(Optional.of(recruitment));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.getRecruitmentParticipants(2L, 11L));

        assertEquals(ErrorCode.RECRUITMENT_NOT_FOUND, error.getErrorCode());
        verifyNoInteractions(participants);
    }

    @Test
    void authorCanReadParticipantsOfOwnHiddenPost() {
        Recruitments recruitment = recruitment();
        ReflectionTestUtils.setField(recruitment, "visibility", RecruitmentVisibility.HIDDEN);
        when(recruitments.findById(11L)).thenReturn(Optional.of(recruitment));
        var host = row(1L, RecruitmentParticipantRole.HOST, now.minusHours(2));
        when(participants.findParticipantRows(11L, RecruitmentParticipantStatus.JOINED))
                .thenReturn(List.of(host));

        var response = service.getRecruitmentParticipants(1L, 11L);

        assertTrue(response.getParticipants().getFirst().isMe());
        assertEquals(hashIds.encode(1L), response.getParticipants().getFirst().getUserId());
    }

    @Test
    void missingPostDoesNotQueryParticipants() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.getRecruitmentParticipants(2L, 99L));
        assertEquals(ErrorCode.RECRUITMENT_NOT_FOUND, error.getErrorCode());
        verifyNoInteractions(participants);
    }

    @Test
    void removalDecodesPublicIdAndReturnsItAfterAutomaticReopening() {
        Recruitments recruitment = recruitment();
        ReflectionTestUtils.setField(recruitment, "status", RecruitmentStatus.CLOSED);
        ReflectionTestUtils.setField(recruitment, "closeReason", RecruitmentCloseReason.FULL);
        ReflectionTestUtils.setField(recruitment, "nowParticipants", 3);
        RecruitmentParticipants target = RecruitmentParticipants.joinAsMember(recruitment, member, now.minusHours(1));
        when(recruitments.findByIdForUpdate(11L)).thenReturn(Optional.of(recruitment));
        when(participants.findByRecruitment_IdAndUser_Id(11L, 2L)).thenReturn(Optional.of(target));
        String targetPublicId = hashIds.encode(2L);

        var response = service.removeParticipant(1L, 11L, RecruitmentParticipantRemoveRequestDto.builder()
                .targetUserId(targetPublicId).removalReason("  제외 사유  ").build());

        assertEquals(targetPublicId, response.getTargetUserId());
        assertNotEquals("2", response.getTargetUserId());
        assertEquals(RecruitmentParticipantStatus.REMOVED, response.getParticipantStatus());
        assertEquals("제외 사유", response.getRemovalReason());
        assertNotNull(response.getLeftAt());
        assertEquals(2, response.getNowParticipants());
        assertEquals(RecruitmentStatus.OPEN, response.getStatus());
        assertNull(recruitment.getCloseReason());
        verify(participants).findByRecruitment_IdAndUser_Id(11L, 2L);
    }

    @Test
    void invalidPublicIdDoesNotQueryOrModifyTarget() {
        Recruitments recruitment = recruitment();
        when(recruitments.findByIdForUpdate(11L)).thenReturn(Optional.of(recruitment));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.removeParticipant(1L, 11L, invalidIdRequest()));

        assertEquals(ErrorCode.INVALID_INPUT, error.getErrorCode());
        assertEquals(2, recruitment.getNowParticipants());
        verifyNoInteractions(participants);
    }

    @ParameterizedTest
    @CsvSource({
            "HIDDEN,OPEN,1,RECRUITMENT_HIDDEN",
            "VISIBLE,OPEN,2,NOT_RECRUITMENT_AUTHOR",
            "VISIBLE,ENDED,1,INVALID_RECRUITMENT_STATUS_TRANSITION"
    })
    void visibilityOwnershipAndLifecycleChecksPrecedePublicIdDecoding(
            RecruitmentVisibility visibility, RecruitmentStatus status, Long actor, ErrorCode expected
    ) {
        Recruitments recruitment = recruitment();
        ReflectionTestUtils.setField(recruitment, "visibility", visibility);
        ReflectionTestUtils.setField(recruitment, "status", status);
        when(recruitments.findByIdForUpdate(11L)).thenReturn(Optional.of(recruitment));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.removeParticipant(actor, 11L, invalidIdRequest()));

        assertEquals(expected, error.getErrorCode());
        verifyNoInteractions(participants);
    }

    private RecruitmentParticipantRemoveRequestDto invalidIdRequest() {
        return RecruitmentParticipantRemoveRequestDto.builder().targetUserId("!").removalReason("제외 사유").build();
    }

    private Recruitments recruitment() {
        Recruitments recruitment = Recruitments.builder().author(author).title("모집글")
                .category(RecruitmentCategory.MEAL).status(RecruitmentStatus.OPEN)
                .visibility(RecruitmentVisibility.VISIBLE).nowParticipants(2).maxParticipants(3)
                .deadlineAt(now.plusDays(1)).activityAt(now.plusDays(2)).build();
        ReflectionTestUtils.setField(recruitment, "id", 11L);
        return recruitment;
    }

    private RecruitmentParticipantsRepository.ParticipantRow row(Long userId, RecruitmentParticipantRole role,
                                                               LocalDateTime joinedAt) {
        var row = mock(RecruitmentParticipantsRepository.ParticipantRow.class);
        when(row.getUserId()).thenReturn(userId);
        when(row.getNickname()).thenReturn("사용자" + userId);
        when(row.getProfileImageUrl()).thenReturn("https://example.com/" + userId + ".png");
        when(row.getRole()).thenReturn(role);
        when(row.getJoinedAt()).thenReturn(joinedAt);
        return row;
    }

    private Users user(Long id) {
        Users user = Users.builder().nickname("사용자" + id).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
