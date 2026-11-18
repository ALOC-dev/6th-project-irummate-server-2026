package com.irummate.domain.recruitment.entity;

import com.irummate.domain.user.entity.Users;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "recruitment_bookmarks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_recruitment_bookmark",
                columnNames = {"user_id", "recruitment_id"}
        ),
        indexes = @Index(
                name = "idx_recruitment_bookmark_user_time",
                columnList = "user_id, bookmarked_at, id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecruitmentBookmarks {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 즐겨찾기를 등록한 사용자
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private Users user;

    // 즐겨찾기 대상 모집글
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recruitment_id", nullable = false, updatable = false)
    private Recruitments recruitment;

    // 즐겨찾기 등록 시각. 내 즐겨찾기 목록의 커서 정렬 기준
    @Column(name = "bookmarked_at", nullable = false, updatable = false)
    private LocalDateTime bookmarkedAt;

    private RecruitmentBookmarks(Recruitments recruitment, Users user, LocalDateTime bookmarkedAt) {
        this.recruitment = recruitment;
        this.user = user;
        this.bookmarkedAt = bookmarkedAt;
    }

    // 즐겨찾기 해제 시에는 상태 변경 없이 해당 기록을 삭제
    public static RecruitmentBookmarks create(Recruitments recruitment, Users user, LocalDateTime bookmarkedAt) {
        return new RecruitmentBookmarks(recruitment, user, bookmarkedAt);
    }
}
