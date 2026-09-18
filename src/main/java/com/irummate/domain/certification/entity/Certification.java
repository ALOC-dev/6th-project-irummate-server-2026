package com.irummate.domain.certification.entity;

import com.irummate.domain.user.entity.Users;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "certifications",
        uniqueConstraints = @UniqueConstraint(name = "uk_certification_user_semester", columnNames = {"user_id", "semester"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Certification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(nullable = false, length = 32)
    private String semester;

    @Column(name = "image_key", nullable = false, columnDefinition = "TEXT")
    private String imageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "certification_status", nullable = false, length = 20)
    private CertificationStatus certificationStatus = CertificationStatus.REQUESTED;

    @Column(name = "admin_comment", columnDefinition = "TEXT")
    private String adminComment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Certification(Users user, String semester, String imageKey, CertificationStatus certificationStatus, String adminComment) {
        this.user = user;
        this.semester = semester;
        this.imageKey = imageKey;
        this.certificationStatus = certificationStatus == null
                ? CertificationStatus.REQUESTED
                : certificationStatus;
        this.adminComment = adminComment;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void approve(String adminComment) {
        this.certificationStatus = CertificationStatus.APPROVED;
        this.adminComment = adminComment;
    }

    public void reject(String adminComment) {
        this.certificationStatus = CertificationStatus.REJECTED;
        this.adminComment = adminComment;
    }

    public void resubmit(String imageKey) {
        this.imageKey = imageKey;
        this.certificationStatus = CertificationStatus.REQUESTED;
        this.adminComment = null;
    }
}
