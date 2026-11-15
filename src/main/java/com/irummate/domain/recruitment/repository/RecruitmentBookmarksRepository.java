package com.irummate.domain.recruitment.repository;

import com.irummate.domain.recruitment.entity.RecruitmentBookmarks;
import com.irummate.domain.recruitment.entity.RecruitmentParticipantStatus;
import com.irummate.domain.recruitment.entity.RecruitmentVisibility;
import com.irummate.domain.recruitment.entity.Recruitments;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;

public interface RecruitmentBookmarksRepository extends JpaRepository<RecruitmentBookmarks, Long> {

    boolean existsByUser_IdAndRecruitment_Id(Long userId, Long recruitmentId);

    // 참여 이력의 즐겨찾기는 페이지 전체를 한 번에 확인
    @Query("SELECT b.recruitment.id FROM RecruitmentBookmarks b WHERE b.user.id = :userId AND b.recruitment.id IN :recruitmentIds")
    List<Long> findBookmarkedRecruitmentIds(@Param("userId") Long userId,
                                          @Param("recruitmentIds") Collection<Long> recruitmentIds);

    // 내 즐겨찾기를 등록순으로 조회. 숨김 글은 제외하고 참여 상태도 함께 조회
    @Query("""
            select b.id as bookmarkId,
                   b.bookmarkedAt as bookmarkedAt,
                   r as recruitment,
                   a.id as authorId,
                   a.nickname as authorNickname,
                   a.profileImageUrl as authorProfileImageUrl,
                   p.status as participantStatus
            from RecruitmentBookmarks b
            join b.recruitment r
            join r.author a
            left join RecruitmentParticipants p
                on p.recruitment = r and p.user.id = :userId
            where b.user.id = :userId
              and r.visibility = :visibility
              and (
                  :cursorId is null
                  or b.bookmarkedAt < :cursorBookmarkedAt
                  or (b.bookmarkedAt = :cursorBookmarkedAt and b.id < :cursorId)
              )
            order by b.bookmarkedAt desc, b.id desc
            """)
    List<RecruitmentBookmarkRow> findMyBookmarks(
            @Param("userId") Long userId,
            @Param("visibility") RecruitmentVisibility visibility,
            @Param("cursorBookmarkedAt") LocalDateTime cursorBookmarkedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    // 동시에 등록해도 사용자별 게시글 즐겨찾기는 하나만 저장
    @Modifying
    @Query(value = """
            INSERT INTO recruitment_bookmarks (user_id, recruitment_id, bookmarked_at)
            VALUES (:userId, :recruitmentId, :bookmarkedAt)
            ON CONFLICT (user_id, recruitment_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId,
                       @Param("recruitmentId") Long recruitmentId,
                       @Param("bookmarkedAt") LocalDateTime bookmarkedAt);

    // 본인의 즐겨찾기만 삭제. 기록이 없으면 0 반환
    @Modifying
    @Query("DELETE FROM RecruitmentBookmarks b WHERE b.user.id = :userId AND b.recruitment.id = :recruitmentId")
    int deleteBookmark(@Param("userId") Long userId, @Param("recruitmentId") Long recruitmentId);

    interface RecruitmentBookmarkRow {
        Long getBookmarkId();
        LocalDateTime getBookmarkedAt();
        Recruitments getRecruitment();
        Long getAuthorId();
        String getAuthorNickname();
        String getAuthorProfileImageUrl();
        RecruitmentParticipantStatus getParticipantStatus();
    }
}
