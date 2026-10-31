package com.irummate.notice;

import com.irummate.domain.notice.entity.DormitoryNotice;
import com.irummate.domain.notice.repository.NoticeRepository;
import com.irummate.domain.notice.service.NoticeService;
import com.irummate.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NoticeServiceTest {
    NoticeRepository repository = mock(NoticeRepository.class);
    NoticeService service = new NoticeService(repository);

    @Test void rejectsInvalidPaginationBeforeAccessingDatabase() {
        for (int[] pair : List.of(new int[]{-1,10}, new int[]{0,0}, new int[]{0,101},
                new int[]{Integer.MAX_VALUE,100})) {
            assertThrows(BusinessException.class, () -> service.findNotices(pair[0], pair[1], null));
        }
        assertThrows(BusinessException.class, () -> service.findNotices(0, 10, "x".repeat(501)));
        verifyNoInteractions(repository);
    }

    @Test void blankKeywordUsesUnfilteredPageAndRequiredSort() {
        when(repository.findAll(any(Pageable.class))).thenAnswer(call -> {
            Pageable pageable = call.getArgument(0);
            assertEquals(0, pageable.getPageNumber());
            assertEquals(10, pageable.getPageSize());
            assertEquals(Sort.by(Sort.Direction.DESC, "publishedAt", "id"), pageable.getSort());
            return Page.empty(pageable);
        });
        var result = service.findNotices(0, 10, "  ");
        assertTrue(result.getContent().isEmpty());
        verify(repository).findAll(any(Pageable.class));
        verifyNoMoreInteractions(repository);
    }

    @Test void trimsSearchKeywordAndPreservesPageMetadata() {
        when(repository.findByTitleContainingIgnoreCase(eq("생활관"), any(Pageable.class)))
                .thenAnswer(call -> Page.empty(call.getArgument(1)));
        var result = service.findNotices(2, 5, " 생활관 ");
        assertEquals(2, result.getPage());
        assertEquals(5, result.getSize());
        verify(repository).findByTitleContainingIgnoreCase(eq("생활관"), any(Pageable.class));
        verifyNoMoreInteractions(repository);
    }

    @Test void mapsOnlyPublicNoticeFields() {
        DormitoryNotice notice = mock(DormitoryNotice.class);
        when(notice.getId()).thenReturn(1L);
        when(notice.getTitle()).thenReturn("공지사항");
        when(notice.getPublishedAt()).thenReturn(LocalDate.of(2026, 10, 27));
        when(notice.getUrl()).thenReturn("https://dormitory.uos.ac.kr/dormitory/web/bbs/dormitory_notice/10102");
        when(repository.findAll(any(Pageable.class))).thenAnswer(call -> new PageImpl<>(List.of(notice), call.getArgument(0), 1));
        var result = service.findNotices(0, 10, null);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getTotalPages());
        assertEquals("공지사항", result.getContent().getFirst().getTitle());
        assertEquals(1L, result.getContent().getFirst().getId());
        verify(notice, never()).getExternalId();
    }
}
