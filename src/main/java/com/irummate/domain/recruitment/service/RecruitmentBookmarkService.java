package com.irummate.domain.recruitment.service;

import com.irummate.domain.recruitment.dto.RecruitmentBookmarkCursorDto;
import com.irummate.domain.recruitment.dto.RecruitmentBookmarkListResponseDto;
import com.irummate.domain.recruitment.dto.RecruitmentParticipationView;
import com.irummate.domain.recruitment.dto.RecruitmentResponseDto;
import com.irummate.domain.recruitment.entity.RecruitmentVisibility;
import com.irummate.domain.recruitment.entity.Recruitments;
import com.irummate.domain.recruitment.repository.RecruitmentBookmarksRepository;
import com.irummate.domain.recruitment.repository.RecruitmentsRepository;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecruitmentBookmarkService {

    private static final int MAX_PAGE_SIZE = 50;

    private final RecruitmentBookmarksRepository recruitmentBookmarksRepository;
    private final RecruitmentsRepository recruitmentsRepository;
    private final UsersRepository usersRepository;

    @Transactional(readOnly = true)
    public RecruitmentBookmarkListResponseDto getMyBookmarks(
            Long userId, LocalDateTime cursorBookmarkedAt, Long cursorId, int size
    ) {
        // 커서는 두 값을 함께 전달. size는 1~50으로 제한
        if (userId == null || userId <= 0 || size < 1 || size > MAX_PAGE_SIZE
                || (cursorBookmarkedAt == null) != (cursorId == null)
                || (cursorId != null && cursorId <= 0)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 다음 목록 확인용 1건을 추가 조회. 전체 건수 조회는 생략
        var rows = recruitmentBookmarksRepository.findMyBookmarks(
                userId, RecruitmentVisibility.VISIBLE, cursorBookmarkedAt, cursorId, PageRequest.of(0, size + 1));
        boolean hasNext = rows.size() > size;
        var selectedRows = hasNext ? rows.subList(0, size) : rows;

        List<RecruitmentResponseDto> items = selectedRows.stream()
                .map(row -> RecruitmentResponseDto.from(
                        row.getRecruitment(),
                        RecruitmentParticipationView.from(
                                row.getAuthorId().equals(userId),
                                row.getParticipantStatus()),
                        row.getAuthorId(), row.getAuthorNickname(), row.getAuthorProfileImageUrl(), true
                ))
                .toList();

        // 게시글 ID가 아닌 즐겨찾기 기록 ID를 다음 커서로 사용
        RecruitmentBookmarkCursorDto nextCursor = hasNext
                ? RecruitmentBookmarkCursorDto.builder()
                        .bookmarkedAt(selectedRows.getLast().getBookmarkedAt())
                        .id(selectedRows.getLast().getBookmarkId())
                        .build()
                : null;

        return RecruitmentBookmarkListResponseDto.builder()
                .items(items)
                .hasNext(hasNext)
                .nextCursor(nextCursor)
                .build();
    }

    @Transactional
    public void addBookmark(Long userId, Long recruitmentId) {
        validateIds(userId, recruitmentId);

        // 즐겨찾기를 등록하는 사용자 확인
        if (!usersRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 모집 상태와 관계없이 등록 가능. 숨김 처리된 글은 등록 불가
        Recruitments recruitment = recruitmentsRepository.findById(recruitmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND));
        if (!recruitment.isVisible()) {
            throw new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND);
        }

        // 게시글 잠금 없이 유니크 제약으로 중복 등록 방지
        recruitmentBookmarksRepository.insertIfAbsent(
                userId, recruitmentId, LocalDateTime.now(ZoneId.of("Asia/Seoul")));
    }

    @Transactional
    public void removeBookmark(Long userId, Long recruitmentId) {
        validateIds(userId, recruitmentId);

        // 숨김 여부와 관계없이 해제 가능. 이미 해제된 경우에도 성공
        recruitmentBookmarksRepository.deleteBookmark(userId, recruitmentId);
    }

    // 잘못된 ID가 쿼리에 전달되지 않도록 확인
    private void validateIds(Long userId, Long recruitmentId) {
        if (userId == null || userId <= 0 || recruitmentId == null || recruitmentId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
