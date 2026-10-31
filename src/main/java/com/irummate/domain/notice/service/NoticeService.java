package com.irummate.domain.notice.service;

import com.irummate.domain.notice.dto.NoticeResponseDto;
import com.irummate.domain.notice.dto.NoticesResponseDto;
import com.irummate.domain.notice.repository.NoticeRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class NoticeService {
    private final NoticeRepository noticeRepository;

    public NoticeService(NoticeRepository noticeRepository) {
        this.noticeRepository = noticeRepository;
    }

    @Transactional(readOnly = true)
    public NoticesResponseDto findNotices(int page, int size, String keyword) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "page must be nonnegative and size must be between 1 and 100");
        }
        if (keyword != null && keyword.length() > 500) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "keyword must not exceed 500 characters");
        }
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt", "id"));
        var result = keyword == null || keyword.isBlank()
                ? noticeRepository.findAll(pageable)
                : noticeRepository.findByTitleContainingIgnoreCase(keyword.strip(), pageable);
        return NoticesResponseDto.from(result.map(NoticeResponseDto::from));
    }
}
