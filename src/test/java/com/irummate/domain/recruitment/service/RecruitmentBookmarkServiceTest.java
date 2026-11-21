package com.irummate.domain.recruitment.service;

import com.irummate.domain.recruitment.dto.RecruitmentParticipationView;
import com.irummate.domain.recruitment.entity.*;
import com.irummate.domain.recruitment.repository.RecruitmentBookmarksRepository;
import com.irummate.domain.recruitment.repository.RecruitmentsRepository;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.domain.user.entity.Users;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecruitmentBookmarkServiceTest {
    private final RecruitmentBookmarksRepository bookmarks = mock(RecruitmentBookmarksRepository.class);
    private final RecruitmentsRepository recruitments = mock(RecruitmentsRepository.class);
    private final UsersRepository users = mock(UsersRepository.class);
    private final RecruitmentBookmarkService service = new RecruitmentBookmarkService(bookmarks, recruitments, users);
    private final LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul")).withNano(123456000);

    @Test
    void nextCursorUsesLastReturnedBookmarkInsteadOfPostOrLookaheadRow() {
        var rows = List.of(row(103L, 901L, 1L, null), row(102L, 902L, 1L, null), row(101L, 903L, 1L, null));
        when(bookmarks.findMyBookmarks(2L, RecruitmentVisibility.VISIBLE, null, null, PageRequest.of(0, 3)))
                .thenReturn(rows);

        var response = service.getMyBookmarks(2L, null, null, 2);

        assertEquals(List.of(901L, 902L), response.getItems().stream().map(item -> item.getRecruitmentId()).toList());
        assertTrue(response.getItems().stream().allMatch(item -> item.isBookmarked()));
        assertTrue(response.isHasNext());
        assertEquals(102L, response.getNextCursor().getId());
        assertEquals(now, response.getNextCursor().getBookmarkedAt());
    }

    @Test
    void participationStatesAndJoinabilityUseExistingListRules() {
        var rows = List.of(row(105L, 901L, 2L, RecruitmentParticipantStatus.JOINED),
                row(104L, 902L, 1L, RecruitmentParticipantStatus.JOINED),
                row(103L, 903L, 1L, RecruitmentParticipantStatus.LEFT),
                row(102L, 904L, 1L, RecruitmentParticipantStatus.REMOVED),
                row(101L, 905L, 1L, null));
        when(bookmarks.findMyBookmarks(2L, RecruitmentVisibility.VISIBLE, null, null, PageRequest.of(0, 6)))
                .thenReturn(rows);

        var response = service.getMyBookmarks(2L, null, null, 5);

        assertEquals(List.of(RecruitmentParticipationView.MINE, RecruitmentParticipationView.JOINED,
                        RecruitmentParticipationView.LEFT, RecruitmentParticipationView.REMOVED, RecruitmentParticipationView.NONE),
                response.getItems().stream().map(item -> item.getParticipating()).toList());
        assertEquals(List.of(false, false, false, false, true),
                response.getItems().stream().map(item -> item.isJoinable()).toList());
        assertFalse(response.isHasNext());
        assertNull(response.getNextCursor());
    }

    @Test
    void emptyResultForwardsCursorWithoutOffsetAndHasNoNextCursor() {
        when(bookmarks.findMyBookmarks(2L, RecruitmentVisibility.VISIBLE, now, 100L, PageRequest.of(0, 51)))
                .thenReturn(List.of());

        var response = service.getMyBookmarks(2L, now, 100L, 50);

        assertTrue(response.getItems().isEmpty());
        assertFalse(response.isHasNext());
        assertNull(response.getNextCursor());
        verify(bookmarks).findMyBookmarks(2L, RecruitmentVisibility.VISIBLE, now, 100L, PageRequest.of(0, 51));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 51, 100000})
    void invalidListSizeIsRejectedBeforeQuerying(int size) {
        assertEquals(ErrorCode.INVALID_INPUT, assertThrows(BusinessException.class,
                () -> service.getMyBookmarks(2L, null, null, size)).getErrorCode());
        verifyNoInteractions(bookmarks, recruitments, users);
    }

    @ParameterizedTest
    @CsvSource({"true,false,1", "false,true,1", "true,true,0", "true,true,-1"})
    void incompleteOrInvalidListCursorIsRejected(boolean hasTime, boolean hasId, long id) {
        assertEquals(ErrorCode.INVALID_INPUT, assertThrows(BusinessException.class,
                () -> service.getMyBookmarks(2L, hasTime ? now : null, hasId ? id : null, 10)).getErrorCode());
        verifyNoInteractions(bookmarks, recruitments, users);
    }

    private RecruitmentBookmarksRepository.RecruitmentBookmarkRow row(
            Long bookmarkId, Long postId, Long authorId, RecruitmentParticipantStatus status
    ) {
        Users author = Users.builder().nickname("작성자").build();
        ReflectionTestUtils.setField(author, "id", authorId);
        Recruitments recruitment = Recruitments.builder().author(author).title("즐겨찾기 모집글")
                .category(RecruitmentCategory.MEAL).status(RecruitmentStatus.OPEN)
                .visibility(RecruitmentVisibility.VISIBLE).nowParticipants(1).maxParticipants(3)
                .createdAt(now.minusDays(1)).deadlineAt(now.plusDays(1)).activityAt(now.plusDays(2)).build();
        ReflectionTestUtils.setField(recruitment, "id", postId);
        var row = mock(RecruitmentBookmarksRepository.RecruitmentBookmarkRow.class);
        when(row.getBookmarkId()).thenReturn(bookmarkId);
        when(row.getBookmarkedAt()).thenReturn(now);
        when(row.getRecruitment()).thenReturn(recruitment);
        when(row.getAuthorId()).thenReturn(authorId);
        when(row.getAuthorNickname()).thenReturn("작성자");
        when(row.getParticipantStatus()).thenReturn(status);
        return row;
    }

    @Test
    void repeatedRegistrationSucceedsWithoutLockingPost() {
        Recruitments recruitment = mock(Recruitments.class);
        when(recruitment.isVisible()).thenReturn(true);
        when(users.existsById(2L)).thenReturn(true);
        when(recruitments.findById(11L)).thenReturn(Optional.of(recruitment));
        when(bookmarks.insertIfAbsent(eq(2L), eq(11L), any())).thenReturn(1, 0);

        assertDoesNotThrow(() -> service.addBookmark(2L, 11L));
        assertDoesNotThrow(() -> service.addBookmark(2L, 11L));

        verify(recruitments, times(2)).findById(11L);
        verify(recruitments, never()).findByIdForUpdate(anyLong());
        verify(bookmarks, times(2)).insertIfAbsent(eq(2L), eq(11L), any());
    }

    @Test
    void hiddenAndMissingPostsReturnSameErrorWithoutInserting() {
        Recruitments hidden = mock(Recruitments.class);
        when(users.existsById(2L)).thenReturn(true);
        when(recruitments.findById(11L)).thenReturn(Optional.of(hidden));

        assertEquals(ErrorCode.RECRUITMENT_NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.addBookmark(2L, 11L)).getErrorCode());
        assertEquals(ErrorCode.RECRUITMENT_NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.addBookmark(2L, 99L)).getErrorCode());
        verifyNoInteractions(bookmarks);
    }

    @Test
    void missingUserCannotRegisterBookmark() {
        assertEquals(ErrorCode.USER_NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.addBookmark(2L, 11L)).getErrorCode());
        verifyNoInteractions(recruitments, bookmarks);
    }

    @Test
    void removalSucceedsWithoutCheckingHiddenOrMissingPost() {
        when(bookmarks.deleteBookmark(2L, 11L)).thenReturn(1, 0);

        assertDoesNotThrow(() -> service.removeBookmark(2L, 11L));
        assertDoesNotThrow(() -> service.removeBookmark(2L, 11L));

        verify(bookmarks, times(2)).deleteBookmark(2L, 11L);
        verifyNoInteractions(recruitments, users);
    }

    @Test
    void invalidIdsAreRejectedBeforeQuerying() {
        assertEquals(ErrorCode.INVALID_INPUT, assertThrows(BusinessException.class,
                () -> service.addBookmark(null, 11L)).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, assertThrows(BusinessException.class,
                () -> service.addBookmark(2L, 0L)).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, assertThrows(BusinessException.class,
                () -> service.removeBookmark(-1L, 11L)).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, assertThrows(BusinessException.class,
                () -> service.removeBookmark(2L, null)).getErrorCode());
        verifyNoInteractions(bookmarks, recruitments, users);
    }
}
