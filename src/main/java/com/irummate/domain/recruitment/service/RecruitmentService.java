package com.irummate.domain.recruitment.service;

import com.irummate.domain.recruitment.dto.*;
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

import java.time.LocalDate;
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
                .orElseThrow(()-> new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND));

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
    public List<RecruitmentResponseDto> getRecruitments(
            Long userId,
            int page,
            int size
    ){

        Pageable pageable = PageRequest.of(page, size);

        return recruitmentsRepository.findRecruitmentList(
                userId,
                RecruitmentParticipantStatus.JOINED,
                RecruitmentVisibility.VISIBLE,
                pageable
                )
                .map(row -> RecruitmentResponseDto.from(
                        row.getRecruitment(),
                        RecruitmentResponseDto.ParticipationView.valueOf(
                                row.getParticipating()
                        )
                ))
                .getContent();
    }


    @Transactional
    public RecruitmentCreateResponseDto createRecruitment(
            Long userId,
            RecruitmentCreateRequestDto request){

        // 사용자가 문제가 없는 사용자인지 확인
        Users user = usersRepository.findById(userId)
                .orElseThrow(()->new BusinessException(ErrorCode.USER_NOT_FOUND));


        // 모집 마감은 활동 시작보다 빨라야 함
        if (!request.getDeadlineAt().isBefore(request.getActivityAt())) {
            throw new BusinessException(ErrorCode.INVALID_RECRUITMENT_PERIOD);
        }


        // 게시글 생성
        // 카테고리별 사용 attribute 다르기에 category에 따라 일부 attribute null값 적용
        Recruitments recruitment = createRecruitmentEntity(user, request);

        // 게시글 작성자 참여
        RecruitmentParticipants participant = RecruitmentParticipants.createHost(recruitment, user, LocalDateTime.now(ZoneId.of("Asia/Seoul")));

        // 게시글 DB에 저장
        Recruitments savedRecruitment = recruitmentsRepository.save(recruitment);

        // 작성자 DB에 저장
        recruitmentParticipantsRepository.save(participant);

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
                .maxParticipants(includeHostInCapacity(request.getMaxParticipants()))
                .estimatedFee(request.getEstimatedFee())
                .status(RecruitmentStatus.OPEN)
                .visibility(RecruitmentVisibility.VISIBLE)
                .nowParticipants(1)
                .build();
    }


    private int includeHostInCapacity(int requestedParticipants) {
        if (requestedParticipants < 1 || requestedParticipants == Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return requestedParticipants + 1;
    }

    // 게시글 수정
    @Transactional
    public RecruitmentDetailResponseDto updateRecruitment(
            Long userId,
            Long recruitmentId,
            RecruitmentUpdateRequestDto request
    ){

        // 게시글 조회
        Recruitments recruitment = recruitmentsRepository.findByIdForUpdate(recruitmentId)
                .orElseThrow(()-> new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND));


        // 요청한 user가 author인지 검증
        if(!recruitment.getAuthor().getId().equals(userId)){
            throw new BusinessException(ErrorCode.NOT_RECRUITMENT_AUTHOR);
        }

        // update request가 제대로 도착했는지
        if (!request.isUpdateRequested()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 게시글 update
        recruitment.update(
                request.getTitle(),
                request.getContent(),
                request.getPlace(),
                request.getActivityAt(),
                request.getDeadlineAt(),
                request.getMaxParticipants() == null
                        ? null : includeHostInCapacity(request.getMaxParticipants()),
                request.getEstimatedFee(),
                LocalDateTime.now(ZoneId.of("Asia/Seoul"))
        );

        recruitmentsRepository.flush();

        boolean participating = recruitmentParticipantsRepository
                .existsByRecruitment_IdAndUser_IdAndStatus(
                        recruitmentId,
                        userId,
                        RecruitmentParticipantStatus.JOINED
                );

        return RecruitmentDetailResponseDto.from(
                recruitment,
                true,
                participating,
                false
                );
    }


    @Transactional
    public RecruitmentDetailResponseDto closeRecruitment(
            Long userId,
            Long recruitmentId
    ){

        // 게시글 조회
        Recruitments recruitment = recruitmentsRepository.findByIdForUpdate(recruitmentId)
                .orElseThrow(()-> new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND));


        // user가 게시글의 Author인지 확인
        if(!recruitment.getAuthor().getId().equals(userId)){
            throw new BusinessException(ErrorCode.NOT_RECRUITMENT_AUTHOR);
        }


        // 현재 시간
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        // 닫을 수 있는 상태인지 검증하고 게시글 close
        recruitment.close(now);


        // 적용
        recruitmentsRepository.flush();


        // 결과 반환
        return RecruitmentDetailResponseDto.from(
                recruitment,
                true,
                true,
                false
        );

    }


    @Transactional
    public RecruitmentDetailResponseDto reopenRecruitment(
            Long userId,
            Long recruitmentId
    ){

        // recruitment 조회
        Recruitments recruitment = recruitmentsRepository.findByIdForUpdate(recruitmentId)
                .orElseThrow(()->new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND));


        // user가 게시글의 author인지 확인
        if(!recruitment.getAuthor().getId().equals(userId)){
            throw new BusinessException(ErrorCode.NOT_RECRUITMENT_AUTHOR);
        }


        // 게시글 모집 재개
        recruitment.reopen(LocalDateTime.now(ZoneId.of("Asia/Seoul")));

        recruitmentsRepository.flush();

        return RecruitmentDetailResponseDto.from(
                recruitment,
                true,
                true,
                false
        );
    }



    @Transactional
    public RecruitmentDetailResponseDto cancelRecruitment(
            Long userId,
            Long recruitmentId,
            RecruitmentCancelRequestDto request
    ){

        // 게시글 조회
        Recruitments recruitment = recruitmentsRepository.findByIdForUpdate(recruitmentId)
                .orElseThrow(()->new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND));


        // user가 게시글의 author인지 확인
        if(!recruitment.getAuthor().getId().equals(userId)){
            throw new BusinessException(ErrorCode.NOT_RECRUITMENT_AUTHOR);
        }

        // 취소
        recruitment.cancel(request.getCancelReason(), LocalDateTime.now(ZoneId.of("Asia/Seoul")));


        recruitmentsRepository.flush();

        return RecruitmentDetailResponseDto.from(
                recruitment,
                true,
                true,
                false
        );
    }

    @Transactional
    public RecruitmentDetailResponseDto completeRecruitment(
            Long userId,
            Long recruitmentId
    ){

        // 게시글 조회
        Recruitments recruitment = recruitmentsRepository.findByIdForUpdate(recruitmentId)
                .orElseThrow(()->new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND));


        // user가 게시글의 author인지 확인
        if(!recruitment.getAuthor().getId().equals(userId)){
            throw new BusinessException(ErrorCode.NOT_RECRUITMENT_AUTHOR);
        }


        // 활동 완료 처리
        recruitment.complete(LocalDateTime.now(ZoneId.of("Asia/Seoul")));

        recruitmentsRepository.flush();

        return RecruitmentDetailResponseDto.from(
                recruitment,
                true,
                true,
                false
        );

    }



}
