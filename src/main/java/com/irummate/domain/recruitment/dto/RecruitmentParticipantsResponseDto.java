package com.irummate.domain.recruitment.dto;

import com.irummate.domain.recruitment.entity.RecruitmentParticipantRole;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class RecruitmentParticipantsResponseDto {

    private final Long recruitmentId;

    // 작성자를 포함한 현재 인원과 정원
    private final Integer nowParticipants;
    private final Integer maxParticipants;

    // 현재 참여 중(JOINED)인 사용자만 참여 시각 순으로 포함. 작성자가 맨 앞
    private final List<Participant> participants;

    @Getter
    @Builder
    public static class Participant {

        // 외부 공개용 HashIds. 차단·신고·강제 제외 요청에 그대로 사용
        private final String userId;
        private final String nickname;
        private final String profileImageUrl;

        // HOST 또는 MEMBER
        private final RecruitmentParticipantRole role;
        private final LocalDateTime joinedAt;

        // 조회한 사용자 본인 여부. 클라이언트가 ID 비교 없이 본인을 표시할 때 사용
        private final boolean me;
    }
}
