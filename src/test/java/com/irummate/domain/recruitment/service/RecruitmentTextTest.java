package com.irummate.domain.recruitment.service;

import com.irummate.domain.recruitment.dto.RecruitmentCreateRequestDto;
import com.irummate.domain.recruitment.dto.RecruitmentUpdateRequestDto;
import com.irummate.domain.recruitment.entity.RecruitmentCategory;
import com.irummate.domain.recruitment.entity.RecruitmentStatus;
import com.irummate.domain.recruitment.entity.RecruitmentVisibility;
import com.irummate.domain.recruitment.entity.Recruitments;
import com.irummate.domain.recruitment.repository.RecruitmentParticipantsRepository;
import com.irummate.domain.recruitment.repository.RecruitmentsRepository;
import com.irummate.domain.recruitment.repository.RecruitmentBookmarksRepository;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.time.ZoneId;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RecruitmentTextTest {
    private final LocalDateTime now = LocalDateTime.of(2035, 1, 1, 12, 0);
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void jsonInputIsStrippedBeforeLengthValidation() {
        String text = "가".repeat(255);
        String json = "{\"title\":\"  " + text + "  \",\"place\":\"  " + text + "  \"}";
        RecruitmentCreateRequestDto create = mapper.readValue(json, RecruitmentCreateRequestDto.class);
        RecruitmentUpdateRequestDto update = mapper.readValue(json, RecruitmentUpdateRequestDto.class);

        assertEquals(text, create.getTitle());
        assertEquals(text, create.getPlace());
        assertEquals(text, update.getTitle());
        assertEquals(text, update.getPlace());
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validateProperty(create, "title").isEmpty());
            assertTrue(validator.validateProperty(create, "place").isEmpty());
            assertTrue(validator.validateProperty(update, "title").isEmpty());
            assertTrue(validator.validateProperty(update, "place").isEmpty());
        }
    }

    @Test
    void whitespaceOnlyTitlesFailRequestValidation() {
        String json = "{\"title\":\"  \u2003  \"}";
        RecruitmentCreateRequestDto create = mapper.readValue(json, RecruitmentCreateRequestDto.class);
        RecruitmentUpdateRequestDto update = mapper.readValue(json, RecruitmentUpdateRequestDto.class);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validateProperty(create, "title").isEmpty());
            assertFalse(validator.validateProperty(update, "title").isEmpty());
        }
    }

    @Test
    void creationNormalizesTextEvenWhenRequestIsBuiltWithoutJsonSetters() {
        RecruitmentsRepository recruitments = mock(RecruitmentsRepository.class);
        UsersRepository users = mock(UsersRepository.class);
        RecruitmentService service = new RecruitmentService(recruitments,
                mock(RecruitmentParticipantsRepository.class), users, mock(RecruitmentBookmarksRepository.class), Clock.system(ZoneId.of("Asia/Seoul")));
        when(users.findById(1L)).thenReturn(Optional.of(Users.builder().nickname("작성자").build()));
        when(recruitments.save(any(Recruitments.class))).thenAnswer(invocation -> {
            Recruitments saved = invocation.getArgument(0);
            assertEquals("밥 친구", saved.getTitle());
            assertNull(saved.getPlace());
            return saved;
        });

        service.createRecruitment(1L, RecruitmentCreateRequestDto.builder()
                .category(RecruitmentCategory.MEAL).title(" \t밥 친구\u2003 ")
                .place(" \t\u2003 ").activityAt(now.plusDays(2))
                .maxParticipants(2).build());

        verify(recruitments).save(any(Recruitments.class));
    }

    @Test
    void partialUpdateStripsBothTextFields() {
        Recruitments recruitment = recruitment();
        recruitment.update("  새 제목  ", " \t학생회관\u2003 ", null, null, null, null, now);

        assertEquals("새 제목", recruitment.getTitle());
        assertEquals("학생회관", recruitment.getPlace());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\u2003"})
    void blankPlaceIsRecognizedAsAnUpdateAndClearsExistingPlace(String place) {
        RecruitmentUpdateRequestDto request = mapper.readValue(
                mapper.writeValueAsString(java.util.Map.of("place", place)), RecruitmentUpdateRequestDto.class);
        assertTrue(request.isUpdateRequested());

        Recruitments recruitment = recruitment();
        recruitment.update(null, request.getPlace(), null, null, null, null, now);
        assertNull(recruitment.getPlace());
        assertEquals("기존 제목", recruitment.getTitle());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"title\":\"새 제목\"}", "{\"title\":\"새 제목\",\"place\":null}"})
    void omittedOrNullPlacePreservesExistingPlace(String json) {
        RecruitmentUpdateRequestDto request = mapper.readValue(json, RecruitmentUpdateRequestDto.class);
        Recruitments recruitment = recruitment();
        recruitment.update(request.getTitle(), request.getPlace(), null, null, null, null, now);

        assertEquals("새 제목", recruitment.getTitle());
        assertEquals("기존 장소", recruitment.getPlace());
    }

    @Test
    void invalidNormalizedTextDoesNotPartiallyChangeEntity() {
        Recruitments recruitment = recruitment();
        BusinessException error = assertThrows(BusinessException.class,
                () -> recruitment.update("  ", "새 장소", null, null, null, null, now));
        assertEquals(ErrorCode.INVALID_INPUT, error.getErrorCode());
        assertThrows(BusinessException.class,
                () -> recruitment.update("새 제목", "가".repeat(256), null, null, null, null, now));
        assertThrows(BusinessException.class,
                () -> recruitment.configureText("가".repeat(256), null));
        assertEquals("기존 제목", recruitment.getTitle());
        assertEquals("기존 장소", recruitment.getPlace());
    }

    private Recruitments recruitment() {
        return Recruitments.builder().title("기존 제목").place("기존 장소")
                .category(RecruitmentCategory.MEAL).status(RecruitmentStatus.OPEN)
                .visibility(RecruitmentVisibility.VISIBLE).activityAt(now.plusDays(2))
                .deadlineAt(now.plusDays(1)).nowParticipants(1).maxParticipants(3).build();
    }
}
