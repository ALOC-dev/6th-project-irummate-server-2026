package com.irummate.domain.matching.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MatchingResponseDto {

    // 외부 공개용 userId;
    private String userId;

    // 학생 이름
    private String name;

    // 성별
    private String gender;

    // 나이
    private Integer age;

    // 자기소개
    private String introduce;

    // 프로필 이미지 URL
    private String imageUrl;

    // 학과, 학번
    private String department;

    // 매칭 점수
    private Double matchPercentage;

    //매칭 상태
    private MatchCardStatus matchStatus;

    // 매칭된 날짜
    private LocalDateTime matchDate;

    // 흡연 여부
    private Integer smokingStatus;

    // 요청자 선택 응답
    List<PreferredAnswerDto> preferredAnswers;

}
