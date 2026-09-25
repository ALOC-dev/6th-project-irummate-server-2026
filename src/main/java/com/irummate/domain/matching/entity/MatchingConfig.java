package com.irummate.domain.matching.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Getter
@Table(name = "matching_config")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingConfig {

    public static final Long SINGLETON_ID = 1L;

    @Id
    @Column(name = "config_id")
    private Long id = SINGLETON_ID;

    @Column(name = "survey_start_date", nullable = false)
    private LocalDate surveyStartDate;

    @Column(name = "match_start_date", nullable = false)
    private LocalDate matchStartDate;

    @Column(name = "match_end_date", nullable = false)
    private LocalDate matchEndDate;

    @Column(name = "updated_at", nullable = false)
    private  LocalDateTime updatedAt;

    @PreUpdate
    @PrePersist
    public void update(){
        this.updatedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    public MatchingConfig(LocalDate surveyStartDate,
                          LocalDate matchStartDate,
                          LocalDate matchEndDate){
        this.id = SINGLETON_ID;
        this.surveyStartDate = surveyStartDate;
        this.matchStartDate = matchStartDate;
        this.matchEndDate = matchEndDate;
    }

    public void updateSurveyStartDate(LocalDate surveyStartDate) { this.surveyStartDate = surveyStartDate; }

    public void updateMatchStartDate(LocalDate matchStartDate) {
        this.matchStartDate = matchStartDate;
    }

    public void updateMatchEndDate(LocalDate matchEndDate){
        this.matchEndDate = matchEndDate;
    }

}
