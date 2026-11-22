package com.irummate.domain.report.entity;

import com.irummate.domain.user.entity.Users;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "user_reports", indexes = {
        @Index(name = "idx_user_reports_created_id", columnList = "created_at DESC, id DESC")
})
@Check(name = "ck_user_reports_not_self", constraints = "reporter_id <> reported_id")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_reports_reporter"))
    private Users reporter; // 신고한 회원

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_reports_reported"))
    private Users reported; // 신고당한 회원

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private ReportSourceType sourceType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Report(Users reporter, Users reported, ReportSourceType sourceType, String reason) {
        this.reporter = reporter;
        this.reported = reported;
        this.sourceType = sourceType;
        this.reason = reason.strip();
    }

    // DB에는 서버의 한국 시간으로 접수 시각을 기록한다.
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }
}
