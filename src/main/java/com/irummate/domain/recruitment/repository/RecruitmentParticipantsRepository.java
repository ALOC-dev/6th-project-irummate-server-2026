package com.irummate.domain.recruitment.repository;

import com.irummate.domain.recruitment.entity.RecruitmentParticipantStatus;
import com.irummate.domain.recruitment.entity.RecruitmentParticipants;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecruitmentParticipantsRepository extends JpaRepository<RecruitmentParticipants, Long> {


    boolean existsByRecruitment_IdAndUser_IdAndStatus(
            Long recruitmentId,
            Long userId,
            RecruitmentParticipantStatus status
    );

    boolean existsByRecruitment_IdAndUser_Id(
            Long recruitmentId,
            Long userId
    );


}
