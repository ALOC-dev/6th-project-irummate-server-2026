package com.irummate.domain.recruitment.service;

import com.irummate.domain.recruitment.dto.RecruitmentCreateRequestDto;
import com.irummate.domain.recruitment.dto.RecruitmentCreateResponseDto;
import com.irummate.domain.recruitment.dto.RecruitmentDetailResponseDto;
import com.irummate.domain.recruitment.dto.RecruitmentResponseDto;
import com.irummate.domain.recruitment.entity.*;
import com.irummate.domain.recruitment.repository.RecruitmentParticipantsRepository;
import com.irummate.domain.recruitment.repository.RecruitmentsRepository;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecruitmentService {

    private final RecruitmentsRepository recruitmentsRepository;
    private final RecruitmentParticipantsRepository recruitmentParticipantsRepository;
    private final UsersRepository usersRepository;


    @Transactional(readOnly = true)
    public RecruitmentDetailResponseDto getRecruitment(
            Long userId,
            Long recruitmentId
    ){

        // recruitment에 대한 유저 정보 계산을 위한 유저 검색
        Users user = usersRepository.findById(userId)
                .orElseThrow(()-> new BusinessException(ErrorCode.USER_NOT_FOUND));


        // recruitment 상세 정보 조회
        Recruitments recruitment = recruitmentsRepository.findById(recruitmentId)
                .orElseThrow(()-> new BusinessException(null));

        // 게시글 소유자 확인
        boolean mine =
                recruitment.getAuthor().getId().equals(userId);

        // 해당 이미 참여중인지 확인
        boolean participating =
                recruitmentParticipantsRepository
                        .existsByRecruitment_IdAndUser_IdAndStatus(
                                recruitmentId,
                                userId,
                                RecruitmentParticipantStatus.JOINED
                        );

        // 게시글이 가득찼는지 여부
        boolean full =
                recruitment.getNowParticipants()
                        >= recruitment.getMaxParticipants();

        // 참여가능한지 여부 판단
        // 본인 게시글이 아닌지 판단
        // 게시글 상태와 공개 상태 확인
        // 인원과 데드라인 확인
        boolean joinable =
                !mine
                        && !participating
                        && recruitment.getStatus() == RecruitmentStatus.OPEN
                        && recruitment.getVisibility()
                        == RecruitmentVisibility.VISIBLE
                        && !full
                        && recruitment.getDeadlineAt().isAfter(
                        LocalDateTime.now(ZoneId.of("Asia/Seoul"))
                );

        // Dto로 변환해 반환
        return RecruitmentDetailResponseDto.from(
                recruitment,
                mine,
                participating,
                joinable
        );


    }


    @Transactional(readOnly = true)
    public List<RecruitmentResponseDto> getRecruitments(int page, int size){

        Pageable pageable = PageRequest.of(page, size);

        Page<Recruitments> recruitments = recruitmentsRepository.findAll(pageable);

        return recruitments.stream()
                .map(RecruitmentResponseDto::from)
                .toList();
    }


    @Transactional
    public RecruitmentCreateResponseDto createRecruitment(
            Long userId,
            RecruitmentCreateRequestDto request){

        // 사용자가 문제가 없는 사용자인지 확인
        Users user = usersRepository.findById(userId)
                .orElseThrow(()->new BusinessException(ErrorCode.USER_NOT_FOUND));


        // 검증 파트 추가 예정


        // 게시글 생성
        // 카테고리별 사용 attribute 다르기에 category에 따라 일부 attribute null값 적용
        Recruitments recruitment = createRecruitmentEntity(user, request);

        // 게시글 작성자 참여
        RecruitmentParticipants.createHost(recruitment, user, LocalDateTime.now(ZoneId.of("Asia/Seoul")));

        // 게시글 DB에 저장
        Recruitments savedRecruitment = recruitmentsRepository.save(recruitment);


        return RecruitmentCreateResponseDto.from(savedRecruitment);
    }


    // 게시글 생성 용 DtoToEntity 함수
    private Recruitments createRecruitmentEntity(
            Users author,
            RecruitmentCreateRequestDto request
    ) {
        return Recruitments.builder()
                .author(author)
                .category(request.getCategory())
                .title(request.getTitle())
                .content(request.getContent())
                .place(request.getPlace())
                .activityAt(request.getActivityAt())
                .deadlineAt(request.getDeadlineAt())
                .maxParticipants(request.getMaxParticipants())
                .estimatedFee(request.getEstimatedFee())
                .status(RecruitmentStatus.OPEN)
                .visibility(RecruitmentVisibility.VISIBLE)
                .nowParticipants(1)
                .build();
    }

}
