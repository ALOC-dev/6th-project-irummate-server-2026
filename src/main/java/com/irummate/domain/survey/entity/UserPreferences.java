package com.irummate.domain.survey.entity;

import com.irummate.domain.survey.dto.SurveyAnswers;
import com.irummate.domain.user.entity.Users;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@Table(name = "user_preferences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class UserPreferences {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private Users user;

    // 하드 코딩용 설문
    @Column(name = "smoking_status", nullable = false)
    private Integer smokingStatus;

    // 자기 소개
    @Column(columnDefinition = "TEXT")
    private String introduce;

    // 여러 설문
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answers", columnDefinition = "jsonb", nullable = false)
    private SurveyAnswers answers;

    // 사용자 개인화 항목(보고 싶은 3가지 항목)
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "visible_profile_fields", columnDefinition = "jsonb")
    private List<SurveyAnswerField> visibleProfileFields = new ArrayList<>();

    // 정규화 벡터
    @Builder.Default
    @Array(length = 9)
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "lifestyle_vector", columnDefinition = "vector(9)")
    private float[] lifestyleVector = new float[9];

    private void convertToNormalizedVector() {
        this.lifestyleVector[0] = normalize(this.answers.getBedtime(), 1, 5);
        this.lifestyleVector[1] = normalize(this.answers.getSnoring(), 1, 5);
        this.lifestyleVector[2] = normalize(this.answers.getSleepTalking(), 1, 5);
        this.lifestyleVector[3] = normalize(this.answers.getOrganizingStyle(), 1, 5);
        this.lifestyleVector[4] = normalize(this.answers.getTemperaturePreference(), 1, 3);
        this.lifestyleVector[5] = normalize(this.answers.getShowerFrequency(), 1 ,4);
        this.lifestyleVector[6] = normalize(this.answers.getSpeakerStyle(), 1, 3);
        this.lifestyleVector[7] = normalize(this.answers.getCallInRoom(), 1, 3);
        this.lifestyleVector[8] = normalize(this.answers.getEatingInRoom(), 1, 3);
    }

    // 벡터 정규화
    private float normalize(Integer value, int min, int max){

        if(value == null)   return 0.0f;

        if(max == min)  return 0.0f;

        if(value < min) value = min;
        if(value > max) value = max;

        return ((float) value - min)/((float) max - min);
    }


    // 메타

    // 설문 완료 여부
    @Builder.Default
    @Column(name = "is_completed", nullable = false)
    private Boolean isCompleted = false;


    // 매칭 성공 여부
    @Builder.Default
    @Column(name = "is_matched", nullable = false)
    private Boolean isMatched = false;

    // 생성 시각(Clock 기준 자동 기록, JpaAuditingConfig)
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // 행이 변경될 때마다 갱신(설문 외 rerolledAt, isMatched 변경 포함)
    // Clock 기준 자동 기록(JpaAuditingConfig)
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "rerolled_at")
    private LocalDateTime rerolledAt;

    // 설문 내용이 마지막으로 저장(최초 제출/수정)된 시각
    // 내부 기록용으로 응답에는 노출하지 않음
    @Column(name = "survey_updated_at")
    private LocalDateTime surveyUpdatedAt;

    @PrePersist
    public void onCreate() {
        convertToNormalizedVector();
        // 엔티티 리스너(AuditingEntityListener)가 엔티티 콜백보다 먼저 실행되므로(JPA 명세)
        // 이 시점에 createdAt이 이미 Clock 기준으로 채워져 있음
        if (this.surveyUpdatedAt == null) {
            this.surveyUpdatedAt = this.createdAt;
        }
    }

    public void updateIsRerolled(LocalDateTime now) {this.rerolledAt = now;}

    public void updateIsMatched() {
        this.isMatched = true;
    }

    public void updatePreferences(Integer smokingStatus,
                                  String introduce,
                                  SurveyAnswers answers,
                                  List<SurveyAnswerField> visibleProfileFields,
                                  LocalDateTime now) {
        this.smokingStatus = smokingStatus;
        this.introduce = introduce;
        this.answers = answers;
        this.visibleProfileFields = visibleProfileFields;
        // 설문이 바뀔 때만 벡터 재계산 및 설문 수정 시각 기록
        convertToNormalizedVector();
        this.surveyUpdatedAt = now;
    }

}
