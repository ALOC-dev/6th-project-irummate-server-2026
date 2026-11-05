package com.irummate.domain.recruitment.service;

import com.irummate.domain.recruitment.entity.RecruitmentParticipants;
import com.irummate.domain.recruitment.entity.RecruitmentStatus;
import com.irummate.domain.recruitment.entity.RecruitmentVisibility;
import com.irummate.domain.recruitment.entity.Recruitments;
import com.irummate.domain.recruitment.repository.RecruitmentParticipantsRepository;
import com.irummate.domain.recruitment.repository.RecruitmentsRepository;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class RecruitmentParticipationService {

    private final RecruitmentsRepository recruitmentsRepository;
    private final RecruitmentParticipantsRepository recruitmentParticipantsRepository;
    private final UsersRepository usersRepository;

    // recruitment에 참여
    @Transactional
    public void joinRecruitment(
            Long userId,
            Long recruitmentId
    ){

        // user 존재 여부를 확인
        Users user = usersRepository.findById(userId)
                .orElseThrow(()->new BusinessException(ErrorCode.USER_NOT_FOUND));


        // 현재 시간
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));


        // Recruitment 존재 여부를 확인
        Recruitments recruitment = recruitmentsRepository.findByIdForUpdate(recruitmentId)
                .orElseThrow(()->new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND));

        // 이전 recruitment 참여 여부를 확인
        boolean participatedBefore = recruitmentParticipantsRepository
                .existsByRecruitment_IdAndUser_Id(recruitmentId, userId);

        // 참여한 적이 있으면 예외처리
        if(participatedBefore){
            throw new BusinessException(ErrorCode.RECRUITMENT_ALREADY_PARTICIPATED);
        }

        // recruitment에 참여 가능한지 검증
        // 1. 본인 recruitment에 참여하는 행위 예외 처리
        if(recruitment.getAuthor().getId().equals(userId)){
            throw new BusinessException(ErrorCode.CANNOT_JOIN_OWN_RECRUITMENT);
        }

        // 2. recruitment 상태 검증
        if(recruitment.getStatus() != RecruitmentStatus.OPEN){
            throw new BusinessException(ErrorCode.RECRUITMENT_NOT_OPEN);
        }

        // 3. recruitment 데드라인 검증
        if(recruitment.getDeadlineAt().isBefore(now)){
            throw new BusinessException(ErrorCode.RECRUITMENT_DEADLINE_PASSED);
        }

        // 4. 참여 인원 검증
        if(recruitment.getMaxParticipants() <= recruitment.getNowParticipants()){
            throw new BusinessException(ErrorCode.RECRUITMENT_FULL);
        }

        // 5. 공개 여부 검증(신고 처리 등)
        if(recruitment.getVisibility() != RecruitmentVisibility.VISIBLE){
            throw new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND);
        }


        // 참여 기록 생성
        RecruitmentParticipants participant = RecruitmentParticipants.joinAsMember(
                recruitment,
                user,
                now
        );


        // recruitment 참여 인원 증가
        // 최대 참여 인원 충족 시 closed
        recruitment.increaseParticipants(now);

        // 참여 기록 저장
        recruitmentParticipantsRepository.save(participant);
    }


    @Transactional
    public void leaveRecruitment(
            Long userId,
            Long recruitmentId
    ){


    }


    @Transactional(readOnly = true)
    public void getMyParticipation(
            Long userId
    ){}


    @Transactional
    public void removeParticipant(
            Long userId,
            Long targetParticipant
    ){}

}
