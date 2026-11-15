package com.irummate.domain.recruitment.service;

import com.irummate.domain.recruitment.entity.*;
import com.irummate.domain.recruitment.repository.RecruitmentParticipantsRepository;
import com.irummate.domain.recruitment.repository.RecruitmentsRepository;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecruitmentJoinValidationTest {
    private final RecruitmentsRepository recruitments = mock(RecruitmentsRepository.class);
    private final RecruitmentParticipantsRepository participants = mock(RecruitmentParticipantsRepository.class);
    private final UsersRepository users = mock(UsersRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final RecruitmentParticipationService service =
            new RecruitmentParticipationService(recruitments, participants, users, mock(HashIdsUtils.class));
    private final LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    private final Users viewer = user(2L);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        when(entityManager.createNativeQuery("SET LOCAL lock_timeout = '1500ms'"))
                .thenReturn(mock(Query.class));
        when(users.findById(2L)).thenReturn(Optional.of(viewer));
    }

    @ParameterizedTest
    @CsvSource({
            "HIDDEN,CLOSED,true,true,true,true,RECRUITMENT_NOT_FOUND",
            "VISIBLE,CLOSED,true,true,true,true,CANNOT_JOIN_OWN_RECRUITMENT",
            "VISIBLE,CLOSED,false,true,true,true,RECRUITMENT_ALREADY_PARTICIPATED",
            "VISIBLE,CLOSED,false,false,true,true,RECRUITMENT_NOT_OPEN",
            "VISIBLE,CANCELLED,false,false,true,true,RECRUITMENT_NOT_OPEN",
            "VISIBLE,ENDED,false,false,true,true,RECRUITMENT_NOT_OPEN",
            "VISIBLE,OPEN,false,false,true,true,RECRUITMENT_DEADLINE_PASSED",
            "VISIBLE,OPEN,false,false,false,true,RECRUITMENT_FULL"
    })
    void preservesErrorPriorityAndDoesNotChangeParticipantsOnFailure(
            RecruitmentVisibility visibility, RecruitmentStatus status, boolean mine,
            boolean history, boolean expired, boolean full, ErrorCode expected
    ) {
        Recruitments recruitment = recruitment(mine ? viewer : user(1L), full ? 3 : 1);
        ReflectionTestUtils.setField(recruitment, "visibility", visibility);
        ReflectionTestUtils.setField(recruitment, "status", status);
        ReflectionTestUtils.setField(recruitment, "deadlineAt", expired ? now.minusDays(1) : now.plusDays(1));
        when(recruitments.findByIdForUpdate(11L)).thenReturn(Optional.of(recruitment));
        when(participants.existsByRecruitment_IdAndUser_Id(11L, 2L)).thenReturn(history);

        BusinessException error = assertThrows(BusinessException.class, () -> service.joinRecruitment(2L, 11L));

        assertEquals(expected, error.getErrorCode());
        assertEquals(full ? 3 : 1, recruitment.getNowParticipants());
        assertEquals(status, recruitment.getStatus());
        verify(participants, never()).save(any());
        if (visibility == RecruitmentVisibility.HIDDEN || mine) {
            verify(participants, never()).existsByRecruitment_IdAndUser_Id(anyLong(), anyLong());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void successfulJoinKeepsQueryOrderAndFullClosure(int initialCount) {
        Recruitments recruitment = recruitment(user(1L), initialCount);
        when(recruitments.findByIdForUpdate(11L)).thenReturn(Optional.of(recruitment));

        service.joinRecruitment(2L, 11L);

        ArgumentCaptor<RecruitmentParticipants> saved = ArgumentCaptor.forClass(RecruitmentParticipants.class);
        var order = inOrder(users, recruitments, participants);
        order.verify(users).findById(2L);
        order.verify(recruitments).findByIdForUpdate(11L);
        order.verify(participants).existsByRecruitment_IdAndUser_Id(11L, 2L);
        order.verify(participants).save(saved.capture());
        assertSame(recruitment, saved.getValue().getRecruitment());
        assertSame(viewer, saved.getValue().getUser());
        assertEquals(RecruitmentParticipantStatus.JOINED, saved.getValue().getStatus());
        assertEquals(RecruitmentParticipantRole.MEMBER, saved.getValue().getRole());
        assertEquals(initialCount + 1, recruitment.getNowParticipants());
        assertEquals(initialCount == 2 ? RecruitmentStatus.CLOSED : RecruitmentStatus.OPEN, recruitment.getStatus());
        assertEquals(initialCount == 2 ? RecruitmentCloseReason.FULL : null, recruitment.getCloseReason());
    }

    @Test
    void deadlineIsCheckedUsingTimeAfterLockAcquisition() {
        Recruitments recruitment = recruitment(user(1L), 1);
        when(recruitments.findByIdForUpdate(11L)).thenAnswer(invocation -> {
            // 락 획득 시점에 마감. 잠금 전에 계산한 과거 시각을 사용하면 참여가 허용될 수 있음
            ReflectionTestUtils.setField(recruitment, "deadlineAt", LocalDateTime.now(ZoneId.of("Asia/Seoul")));
            return Optional.of(recruitment);
        });

        BusinessException error = assertThrows(BusinessException.class, () -> service.joinRecruitment(2L, 11L));

        assertEquals(ErrorCode.RECRUITMENT_DEADLINE_PASSED, error.getErrorCode());
        verify(participants, never()).save(any());
        assertEquals(1, recruitment.getNowParticipants());
    }

    private Recruitments recruitment(Users author, int count) {
        return Recruitments.builder().author(author).category(RecruitmentCategory.MEAL)
                .visibility(RecruitmentVisibility.VISIBLE).status(RecruitmentStatus.OPEN)
                .nowParticipants(count).maxParticipants(3)
                .deadlineAt(now.plusDays(1)).activityAt(now.plusDays(2)).build();
    }

    private Users user(Long id) {
        Users user = Users.builder().nickname("사용자" + id).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
