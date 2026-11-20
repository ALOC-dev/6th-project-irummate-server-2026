package com.irummate.domain.recruitment.service;

import com.irummate.domain.recruitment.dto.RecruitmentSort;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecruitmentCursorPaginationTest {
    private final RecruitmentsRepository repository = mock(RecruitmentsRepository.class);
    private final RecruitmentParticipantsRepository participants = mock(RecruitmentParticipantsRepository.class);
    private final UsersRepository users = mock(UsersRepository.class);
    private final RecruitmentService service = new RecruitmentService(repository, participants, users, mock(RecruitmentBookmarksRepository.class), Clock.system(ZoneId.of("Asia/Seoul")));
    private final LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul")).withNano(123456000);

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 51, 100000})
    void invalidSizeFailsBeforeQuery(int size) {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.getRecruitments(2L, null, RecruitmentSort.LATEST, null, null, null, size));
        assertEquals(ErrorCode.INVALID_INPUT, error.getErrorCode());
        verifyNoInteractions(repository, participants, users);
    }

    @ParameterizedTest
    @CsvSource({"true,false,1", "false,true,1", "true,true,0", "true,true,-1"})
    void incompleteOrNonPositiveCursorFailsBeforeQuery(boolean hasTime, boolean hasId, long id) {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.getRecruitments(2L, null, RecruitmentSort.LATEST, hasTime ? now : null, null, hasId ? id : null, 10));
        assertEquals(ErrorCode.INVALID_INPUT, error.getErrorCode());
        verifyNoInteractions(repository, participants, users);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void looksAheadAndUsesLastReturnedPostForCursor(int resultCount) {
        List<RecruitmentsRepository.RecruitmentListRow> rows = IntStream.range(0, resultCount)
                .mapToObj(index -> row(300L - index, now.minusSeconds(index))).toList();
        when(repository.findRecruitmentList(2L, null, RecruitmentVisibility.VISIBLE,
                null, null, PageRequest.of(0, 3))).thenReturn(rows);

        var response = service.getRecruitments(2L, null, RecruitmentSort.LATEST, null, null, null, 2);

        assertEquals(Math.min(2, resultCount), response.getItems().size());
        assertEquals(resultCount > 2, response.isHasNext());
        if (resultCount > 2) {
            assertEquals(299L, response.getNextCursor().getId());
            assertEquals(now.minusSeconds(1), response.getNextCursor().getCreatedAt());
            assertEquals(299L, response.getItems().getLast().getRecruitmentId());
        } else {
            assertNull(response.getNextCursor());
        }
        verify(repository).findRecruitmentList(2L, null, RecruitmentVisibility.VISIBLE,
                null, null, PageRequest.of(0, 3));
    }

    @Test
    void forwardsCursorAndCategoryWithoutAnOffsetAndAcceptsMaximumSize() {
        when(repository.findRecruitmentList(2L, RecruitmentCategory.MEAL, RecruitmentVisibility.VISIBLE,
                now, 42L, PageRequest.of(0, 51))).thenReturn(List.of());

        var response = service.getRecruitments(2L, RecruitmentCategory.MEAL, RecruitmentSort.LATEST, now, null, 42L, 50);

        assertTrue(response.getItems().isEmpty());
        assertFalse(response.isHasNext());
        assertNull(response.getNextCursor());
        verify(repository).findRecruitmentList(2L, RecruitmentCategory.MEAL, RecruitmentVisibility.VISIBLE,
                now, 42L, PageRequest.of(0, 51));
    }

    private RecruitmentsRepository.RecruitmentListRow row(Long id, LocalDateTime createdAt) {
        Users author = Users.builder().nickname("작성자").build();
        ReflectionTestUtils.setField(author, "id", 1L);
        Recruitments recruitment = Recruitments.builder().author(author).title("모집글")
                .category(RecruitmentCategory.MEAL).status(RecruitmentStatus.OPEN)
                .visibility(RecruitmentVisibility.VISIBLE).nowParticipants(1).maxParticipants(3)
                .createdAt(createdAt).deadlineAt(now.plusDays(1)).activityAt(now.plusDays(2)).build();
        ReflectionTestUtils.setField(recruitment, "id", id);
        var row = mock(RecruitmentsRepository.RecruitmentListRow.class);
        when(row.getRecruitment()).thenReturn(recruitment);
        when(row.getAuthorId()).thenReturn(1L);
        when(row.getAuthorNickname()).thenReturn("작성자");
        return row;
    }
}
