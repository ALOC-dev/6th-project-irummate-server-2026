package com.irummate.domain.block.entity;

import com.irummate.domain.user.entity.Users;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;
import java.time.ZoneId;

// 한 회원이 다른 회원을 차단한 단방향 관계 한 건을 저장한다.
@Entity
@Table(name = "user_blocks",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_blocks_pair", columnNames = {"blocker_id", "blocked_id"}),
        indexes = @Index(name = "idx_user_blocks_reverse", columnList = "blocked_id, blocker_id"))
@Check(name = "ck_user_blocks_not_self", constraints = "blocker_id <> blocked_id")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBlock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_blocks_blocker"))
    private Users blocker; // 차단을 실행한 회원

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_blocks_blocked"))
    private Users blocked; // 차단 대상 회원

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt; // 최초 차단 시각

    // JPA 저장 시 시각을 설정한다. 현재 네이티브 INSERT는 Service에서 시각을 전달한다.
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }
}
