package com.irummate.domain.recruitment.repository;

import com.irummate.domain.recruitment.entity.RecruitmentParticipantStatus;
import com.irummate.domain.recruitment.entity.RecruitmentCategory;
import com.irummate.domain.recruitment.entity.RecruitmentVisibility;
import com.irummate.domain.recruitment.entity.RecruitmentStatus;
import com.irummate.domain.recruitment.entity.Recruitments;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecruitmentsRepository extends JpaRepository<Recruitments, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT r
    FROM Recruitments r
    WHERE r.id = :recruitmentId
    """)
    Optional<Recruitments> findByIdForUpdate(
            @Param("recruitmentId") Long recruitmentId
    );



    // 커서보다 오래된 글만 조회. 내 참여·즐겨찾기와 작성자 필드를 함께 조회
    // 두 커서 값은 함께 검증되므로, 커서 유무는 PostgreSQL에서 null 타입이 명확한 ID로 판단
    @Query("""
        select r as recruitment,
               a.id as authorId,
               a.nickname as authorNickname,
               a.profileImageUrl as authorProfileImageUrl,
               p.status as participantStatus,
               case when b.id is not null then true else false end as bookmarked
        from Recruitments r
        join r.author a
        left join RecruitmentParticipants p
            on p.recruitment = r
            and p.user.id = :userId
        left join RecruitmentBookmarks b
            on b.recruitment = r
            and b.user.id = :userId
        where r.visibility = :visibility
          and (:category is null or r.category = :category)
          and (
              :cursorId is null
              or r.createdAt < :cursorCreatedAt
              or (r.createdAt = :cursorCreatedAt and r.id < :cursorId)
          )
        order by r.createdAt desc, r.id desc
        """)
    List<RecruitmentListRow> findRecruitmentList(
            @Param("userId") Long userId,
            @Param("category") RecruitmentCategory category,
            @Param("visibility") RecruitmentVisibility visibility,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );


    // 24시간 이내 마감 또는 정원 80% 이상인 모집 중 글만 선별한 뒤 페이징
    // 인원은 작성자 포함. 나눗셈 대신 long 곱셈으로 비율과 큰 정원을 정확하게 비교
    @Query("""
        select r as recruitment,
               a.id as authorId,
               a.nickname as authorNickname,
               a.profileImageUrl as authorProfileImageUrl,
               p.status as participantStatus,
               case when b.id is not null then true else false end as bookmarked
        from Recruitments r
        join r.author a
        left join RecruitmentParticipants p
            on p.recruitment = r and p.user.id = :userId
        left join RecruitmentBookmarks b
            on b.recruitment = r and b.user.id = :userId
        where r.visibility = :visibility
          and r.status = :status
          and r.deadlineAt > :now
          and r.activityAt > :now
          and r.nowParticipants < r.maxParticipants
          and (:category is null or r.category = :category)
          and (
              r.deadlineAt <= :closingBefore
              or cast(r.nowParticipants as long) * 100 >= cast(r.maxParticipants as long) * 80
          )
          and (
              :cursorId is null
              or r.deadlineAt > :cursorDeadlineAt
              or (r.deadlineAt = :cursorDeadlineAt and r.id < :cursorId)
          )
        order by r.deadlineAt asc, r.id desc
        """)
    List<RecruitmentListRow> findClosingSoonRecruitmentList(
            @Param("userId") Long userId,
            @Param("category") RecruitmentCategory category,
            @Param("visibility") RecruitmentVisibility visibility,
            @Param("status") RecruitmentStatus status,
            @Param("now") LocalDateTime now,
            @Param("closingBefore") LocalDateTime closingBefore,
            @Param("cursorDeadlineAt") LocalDateTime cursorDeadlineAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    // 활동 시작 시각이 지난 글을 일괄 종료. 다른 요청이 잠근 글은 다음 실행에서 처리
    @Modifying
    @Query(value = """
        WITH target AS (
            SELECT id
            FROM recruitments
            WHERE status IN ('OPEN', 'CLOSED')
              AND activity_at <= :now
            ORDER BY activity_at, id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
        )
        UPDATE recruitments r
        SET status = 'ENDED',
            completed_at = :now,
            closed_at = CASE WHEN r.status = 'OPEN' THEN :now ELSE r.closed_at END,
            updated_at = :now
        FROM target t
        WHERE r.id = t.id
        """, nativeQuery = true)
    int endRecruitmentsInBatch(
            @Param("now") LocalDateTime now,
            @Param("batchSize") int batchSize
    );

    // 활동 시작 전 마감 시각이 지난 글만 일괄 마감. 엔티티 콜백 대신 수정 시각도 직접 갱신
    @Modifying
    @Query(value = """
        WITH target AS (
            SELECT id
            FROM recruitments
            WHERE status = 'OPEN'
              AND deadline_at <= :now
              AND activity_at > :now
            ORDER BY deadline_at, id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
        )
        UPDATE recruitments r
        SET status = 'CLOSED',
            close_reason = 'DEADLINE',
            closed_at = :now,
            updated_at = :now
        FROM target t
        WHERE r.id = t.id
        """, nativeQuery = true)
    int closeRecruitmentsByDeadlineInBatch(
            @Param("now") LocalDateTime now,
            @Param("batchSize") int batchSize
    );

    public interface RecruitmentListRow {
        Recruitments getRecruitment();
        Long getAuthorId();
        String getAuthorNickname();
        String getAuthorProfileImageUrl();
        RecruitmentParticipantStatus getParticipantStatus();
        boolean isBookmarked();
    }
}
