package com.irummate.domain.recruitment.service;

import com.irummate.domain.chat.service.RecruitmentChatRoomService;

import com.irummate.domain.recruitment.dto.RecruitmentSort;
import com.irummate.domain.recruitment.entity.*;
import com.irummate.domain.recruitment.repository.RecruitmentBookmarksRepository;
import com.irummate.domain.recruitment.repository.RecruitmentParticipantsRepository;
import com.irummate.domain.recruitment.repository.RecruitmentsRepository;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecruitmentClosingSoonServiceTest {
    private final RecruitmentsRepository repository = mock(RecruitmentsRepository.class);
    private final RecruitmentService service = new RecruitmentService(repository,
            mock(RecruitmentParticipantsRepository.class), mock(UsersRepository.class), mock(RecruitmentBookmarksRepository.class), mock(RecruitmentChatRoomService.class));
    private static final LocalDateTime NOW = LocalDateTime.now(ZoneId.of("Asia/Seoul")).withNano(123456000);

    @Test
    void closingSoonUsesOneTimeWindowAndReturnsDeadlineCursorFromLastSelectedRow() {
        var rows = List.of(row(11L, NOW.plusHours(1)), row(10L, NOW.plusHours(2)), row(9L, NOW.plusHours(3)));
        when(repository.findClosingSoonRecruitmentList(eq(2L), eq(RecruitmentCategory.MEAL),
                eq(RecruitmentVisibility.VISIBLE), eq(RecruitmentStatus.OPEN), any(), any(),
                isNull(), isNull(), eq(PageRequest.of(0, 3)))).thenReturn(rows);

        var response = service.getRecruitments(2L, RecruitmentCategory.MEAL, RecruitmentSort.CLOSING_SOON,
                null, null, null, 2);

        assertEquals(List.of(11L, 10L), response.getItems().stream().map(item -> item.getRecruitmentId()).toList());
        assertTrue(response.getItems().getFirst().isBookmarked());
        assertTrue(response.isHasNext());
        assertEquals(10L, response.getNextCursor().getId());
        assertEquals(NOW.plusHours(2), response.getNextCursor().getDeadlineAt());
        assertNull(response.getNextCursor().getCreatedAt());
        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> closingBefore = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).findClosingSoonRecruitmentList(eq(2L), eq(RecruitmentCategory.MEAL),
                eq(RecruitmentVisibility.VISIBLE), eq(RecruitmentStatus.OPEN), now.capture(), closingBefore.capture(),
                isNull(), isNull(), eq(PageRequest.of(0, 3)));
        assertEquals(now.getValue().plusHours(24), closingBefore.getValue());
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void lastPageHasNoNextCursorAndForwardsDeadlineBoundary(int count) {
        var rows = Stream.of(row(11L, NOW.plusHours(1)), row(10L, NOW.plusHours(2))).limit(count).toList();
        when(repository.findClosingSoonRecruitmentList(eq(2L), isNull(), eq(RecruitmentVisibility.VISIBLE),
                eq(RecruitmentStatus.OPEN), any(), any(), eq(NOW), eq(12L), eq(PageRequest.of(0, 3)))).thenReturn(rows);

        var response = service.getRecruitments(2L, null, RecruitmentSort.CLOSING_SOON, null, NOW, 12L, 2);

        assertEquals(count, response.getItems().size());
        assertFalse(response.isHasNext());
        assertNull(response.getNextCursor());
    }

    @ParameterizedTest
    @MethodSource("invalidCursors")
    void invalidOrMixedCursorIsRejectedBeforeQuery(RecruitmentSort sort, LocalDateTime createdAt,
                                                  LocalDateTime deadlineAt, Long id) {
        assertEquals(ErrorCode.INVALID_INPUT, assertThrows(BusinessException.class,
                () -> service.getRecruitments(2L, null, sort, createdAt, deadlineAt, id, 10)).getErrorCode());
        verifyNoInteractions(repository);
    }

    static Stream<Arguments> invalidCursors() {
        return Stream.of(
                Arguments.of(null, null, null, null),
                Arguments.of(RecruitmentSort.LATEST, null, NOW, 1L),
                Arguments.of(RecruitmentSort.LATEST, NOW, NOW, 1L),
                Arguments.of(RecruitmentSort.CLOSING_SOON, NOW, null, 1L),
                Arguments.of(RecruitmentSort.CLOSING_SOON, NOW, NOW, 1L),
                Arguments.of(RecruitmentSort.CLOSING_SOON, null, NOW, null),
                Arguments.of(RecruitmentSort.CLOSING_SOON, null, null, 1L),
                Arguments.of(RecruitmentSort.CLOSING_SOON, null, NOW, 0L),
                Arguments.of(RecruitmentSort.CLOSING_SOON, null, NOW, -1L));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 51, 100000})
    void invalidClosingSoonSizeIsRejectedBeforeQuery(int size) {
        assertEquals(ErrorCode.INVALID_INPUT, assertThrows(BusinessException.class,
                () -> service.getRecruitments(2L, null, RecruitmentSort.CLOSING_SOON,
                        null, null, null, size)).getErrorCode());
        verifyNoInteractions(repository);
    }

    private RecruitmentsRepository.RecruitmentListRow row(Long id, LocalDateTime deadlineAt) {
        Recruitments recruitment = Recruitments.builder().id(id).title("마감 임박 글")
                .category(RecruitmentCategory.MEAL).status(RecruitmentStatus.OPEN).visibility(RecruitmentVisibility.VISIBLE)
                .createdAt(NOW.minusDays(id)).deadlineAt(deadlineAt).activityAt(deadlineAt.plusHours(1))
                .nowParticipants(1).maxParticipants(3).build();
        var row = mock(RecruitmentsRepository.RecruitmentListRow.class);
        when(row.getRecruitment()).thenReturn(recruitment);
        when(row.getAuthorId()).thenReturn(1L);
        when(row.isBookmarked()).thenReturn(true);
        return row;
    }
}
