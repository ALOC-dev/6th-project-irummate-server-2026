package com.irummate.domain.recruitment.service;

import com.irummate.domain.recruitment.dto.RecruitmentCreateRequestDto;
import com.irummate.domain.recruitment.dto.RecruitmentUpdateRequestDto;
import com.irummate.domain.recruitment.entity.*;
import com.irummate.domain.recruitment.repository.RecruitmentBookmarksRepository;
import com.irummate.domain.recruitment.repository.RecruitmentParticipantsRepository;
import com.irummate.domain.recruitment.repository.RecruitmentsRepository;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecruitmentScheduleServiceTest {
    private final RecruitmentsRepository recruitments = mock(RecruitmentsRepository.class);
    private final RecruitmentParticipantsRepository participants = mock(RecruitmentParticipantsRepository.class);
    private final UsersRepository users = mock(UsersRepository.class);
    private final RecruitmentService service = new RecruitmentService(recruitments, participants, users,
            mock(RecruitmentBookmarksRepository.class));
    private final LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul")).withNano(0);
    private final JsonMapper mapper = JsonMapper.builder().build();

    @ParameterizedTest
    @EnumSource(RecruitmentCategory.class)
    void creationUsesSameAutomaticScheduleForEveryCategory(RecruitmentCategory category) {
        when(users.findById(1L)).thenReturn(Optional.of(author()));
        when(recruitments.save(any())).thenAnswer(invocation -> {
            Recruitments recruitment = invocation.getArgument(0);
            ReflectionTestUtils.setField(recruitment, "id", 11L);
            return recruitment;
        });
        RecruitmentCreateRequestDto request = RecruitmentCreateRequestDto.builder()
                .category(category).title("모집글").activityAt(now.plusHours(2)).maxParticipants(2)
                .priceType(category == RecruitmentCategory.GROUP_BUY ? RecruitmentPriceType.FIXED : null)
                .amount(category == RecruitmentCategory.GROUP_BUY ? 1000 : null).build();

        service.createRecruitment(1L, request);

        ArgumentCaptor<Recruitments> saved = ArgumentCaptor.forClass(Recruitments.class);
        verify(recruitments).save(saved.capture());
        assertEquals(now.plusMinutes(90), saved.getValue().getDeadlineAt());
        assertEquals(now.plusHours(2), saved.getValue().getActivityAt());
        assertEquals(1, saved.getValue().getNowParticipants());
        assertEquals(3, saved.getValue().getMaxParticipants());
        ArgumentCaptor<RecruitmentParticipants> host = ArgumentCaptor.forClass(RecruitmentParticipants.class);
        verify(participants).save(host.capture());
        assertSame(saved.getValue(), host.getValue().getRecruitment());
        assertEquals(RecruitmentParticipantRole.HOST, host.getValue().getRole());
    }

    @Test
    void creationWithinOneHourDoesNotSavePostOrHost() {
        when(users.findById(1L)).thenReturn(Optional.of(author()));
        var request = RecruitmentCreateRequestDto.builder().category(RecruitmentCategory.MEAL)
                .title("급구").activityAt(now.plusMinutes(59)).maxParticipants(2).build();

        assertEquals(ErrorCode.INVALID_RECRUITMENT_PERIOD, assertThrows(BusinessException.class,
                () -> service.createRecruitment(1L, request)).getErrorCode());
        verify(recruitments, never()).save(any());
        verifyNoInteractions(participants);
    }

    @Test
    void requestNeedsOnlyActivityAndCannotOverrideDeadline() {
        String fields = "\"category\":\"MEAL\",\"title\":\"모집글\",\"activityAt\":\"" + now.plusDays(2)
                + "\",\"maxParticipants\":2";
        var request = mapper.readValue("{" + fields + "}", RecruitmentCreateRequestDto.class);
        var withIgnoredDeadline = mapper.readValue("{" + fields + ",\"deadlineAt\":\"2000-01-01T00:00:00\"}",
                RecruitmentCreateRequestDto.class);
        var deadlineOnlyUpdate = mapper.readValue("{\"deadlineAt\":\"2035-01-01T00:00:00\"}",
                RecruitmentUpdateRequestDto.class);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(request).isEmpty());
            assertTrue(factory.getValidator().validate(withIgnoredDeadline).isEmpty());
            assertFalse(factory.getValidator().validate(deadlineOnlyUpdate).isEmpty());
        }
        assertFalse(mapper.writeValueAsString(request).contains("deadlineAt"));
        assertFalse(deadlineOnlyUpdate.isUpdateRequested());
    }

    @Test
    void updateServiceRecalculatesDeadlineFromNewActivity() {
        EntityManager em = mock(EntityManager.class);
        when(em.createNativeQuery("SET LOCAL lock_timeout = '1500ms'")).thenReturn(mock(Query.class));
        ReflectionTestUtils.setField(service, "entityManager", em);
        Recruitments recruitment = Recruitments.builder().id(11L).author(author()).title("모집글")
                .category(RecruitmentCategory.MEAL).status(RecruitmentStatus.OPEN).visibility(RecruitmentVisibility.VISIBLE)
                .activityAt(now.plusDays(1)).deadlineAt(now.plusDays(1).minusMinutes(30))
                .nowParticipants(1).maxParticipants(3).build();
        when(recruitments.findByIdForUpdate(11L)).thenReturn(Optional.of(recruitment));

        var response = service.updateRecruitment(1L, 11L,
                RecruitmentUpdateRequestDto.builder().activityAt(now.plusHours(2)).build());

        assertEquals(now.plusHours(2), response.getActivityAt());
        assertEquals(now.plusMinutes(90), response.getDeadlineAt());
        verify(recruitments).flush();
    }

    private Users author() {
        Users author = Users.builder().nickname("작성자").build();
        ReflectionTestUtils.setField(author, "id", 1L);
        return author;
    }
}
