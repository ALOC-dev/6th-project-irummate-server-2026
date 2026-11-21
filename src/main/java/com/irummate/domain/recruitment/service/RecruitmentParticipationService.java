package com.irummate.domain.recruitment.service;

import com.irummate.domain.chat.service.RecruitmentChatRoomService;
import com.irummate.domain.recruitment.dto.*;
import com.irummate.domain.recruitment.entity.*;
import com.irummate.domain.recruitment.repository.RecruitmentParticipantsRepository;
import com.irummate.domain.recruitment.repository.RecruitmentsRepository;
import com.irummate.domain.recruitment.repository.RecruitmentBookmarksRepository;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.util.HashIdsUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class RecruitmentParticipationService {

    private static final int MAX_PAGE_SIZE = 50;

    private final RecruitmentsRepository recruitmentsRepository;
    private final RecruitmentParticipantsRepository recruitmentParticipantsRepository;
    private final UsersRepository usersRepository;
    private final HashIdsUtils hashIdsUtils;
    private final RecruitmentBookmarksRepository recruitmentBookmarksRepository;
    private final RecruitmentChatRoomService recruitmentChatRooms;

    @PersistenceContext
    private EntityManager entityManager;

    // 현재 요청의 트랜잭션에서만 잠금 대기를 1.5초로 제한
    private void applyLockTimeout() {
        entityManager.createNativeQuery("SET LOCAL lock_timeout = '1500ms'")
                .executeUpdate();
    }

    // recruitment에 참여
    @Transactional
    public void joinRecruitment(
            Long userId,
            Long recruitmentId
    ){
        applyLockTimeout();

        // user 존재 여부를 확인
        Users user = usersRepository.findById(userId)
                .orElseThrow(()->new BusinessException(ErrorCode.USER_NOT_FOUND));


        // Recruitment 존재 여부를 확인
        Recruitments recruitment = recruitmentsRepository.findByIdForUpdate(recruitmentId)
                .orElseThrow(()->new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND));

        // 잠금 대기 중 마감될 수 있으므로 잠금 획득 후 현재 시간 확인
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        // 숨김 여부를 다른 참여 조건보다 먼저 확인
        if(!recruitment.isVisible()){
            throw new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND);
        }


        // recruitment에 참여 가능한지 검증
        // 1. 본인 recruitment에 참여하는 행위 예외 처리
        if(recruitment.getAuthor().getId().equals(userId)){
            throw new BusinessException(ErrorCode.CANNOT_JOIN_OWN_RECRUITMENT);
        }


        // 2. 이전 recruitment 참여 여부를 확인
        boolean participatedBefore = recruitmentParticipantsRepository
                .existsByRecruitment_IdAndUser_Id(recruitmentId, userId);

        // 참여한 적이 있으면 예외처리
        // 나갔거나 참여 중이면 재참여/입장 거절
        if(participatedBefore){
            throw new BusinessException(ErrorCode.RECRUITMENT_ALREADY_PARTICIPATED);
        }

        // 3~5. 게시글의 상태 -> 마감 -> 정원 조건을 공통 판정
        // 작성자와 참여 이력 검사가 먼저 실패하도록 기존 예외 우선순위 유지
        ErrorCode joinError = recruitment.findJoinError(now);
        if(joinError != null){
            throw new BusinessException(joinError);
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
        recruitmentChatRooms.join(recruitmentId, user, now);
    }


    @Transactional
    public RecruitmentLeaveResponseDto leaveRecruitment(
            Long userId,
            Long recruitmentId
    ){
        applyLockTimeout();

        // recruitment 잠금
        Recruitments recruitment = recruitmentsRepository.findByIdForUpdate(recruitmentId)
                .orElseThrow(()->new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND));


        // 숨겨진 게시글인지 확인
        if (recruitment.getVisibility() != RecruitmentVisibility.VISIBLE) {
            throw new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND);
        }

        // 참여 이력 확인
        RecruitmentParticipants participant = recruitmentParticipantsRepository
                .findByRecruitment_IdAndUser_Id(recruitmentId, userId)
                .orElseThrow(()-> new BusinessException(ErrorCode.RECRUITMENT_PARTICIPANT_NOT_FOUND));


        // 만약 작성자면 거부
        if(participant.getRole() == RecruitmentParticipantRole.HOST){
            throw new BusinessException(ErrorCode.RECRUITMENT_HOST_CANNOT_LEAVE);
        }

        // 참여 상태 확인
        if(participant.getStatus() != RecruitmentParticipantStatus.JOINED){
            throw new BusinessException(ErrorCode.RECRUITMENT_PARTICIPANT_NOT_ACTIVE);
        }

        // 참여 상태 변경
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        if ((recruitment.getStatus() != RecruitmentStatus.OPEN
                && recruitment.getStatus() != RecruitmentStatus.CLOSED)
                || !recruitment.getActivityAt().isAfter(now)) {
            throw new BusinessException(ErrorCode.INVALID_RECRUITMENT_STATUS_TRANSITION);
        }
        participant.leave(now);
        recruitmentChatRooms.leave(recruitmentId, userId, now);


        // 게시글 참여자 수 감소
        recruitment.decreaseParticipants(now);


        return RecruitmentLeaveResponseDto.from(participant);

    }


    // 로그인 사용자의 전체 모집글 참여 이력 조회
    @Transactional(readOnly = true)
    public List<RecruitmentResponseDto> getMyParticipation(
            Long userId,
            int page,
            int size
    ) {
        validatePageRequest(page, size);

        var participations = recruitmentParticipantsRepository
                .findAllByUser_IdOrderByJoinedAtDescIdDesc(userId, PageRequest.of(page, size))
                .getContent();

        // 글마다 조회하지 않고 현재 페이지의 즐겨찾기만 한 번에 조회
        Set<Long> bookmarkedIds = participations.isEmpty() ? Set.of() : new HashSet<>(
                recruitmentBookmarksRepository.findBookmarkedRecruitmentIds(userId,
                        participations.stream().map(participant -> participant.getRecruitment().getId()).toList()));

        List<RecruitmentResponseDto> myParticipants = participations.stream()
                .map(participant ->{
                    Recruitments recruitment = participant.getRecruitment();

                    boolean mine = recruitment.getAuthor().getId().equals(userId);
                    RecruitmentParticipationView participating =
                            RecruitmentParticipationView.from(mine, participant.getStatus());

                    if (!mine
                            && recruitment.getVisibility() == RecruitmentVisibility.HIDDEN) {
                        return RecruitmentResponseDto.builder()
                                .recruitmentId(recruitment.getId())
                                .title("숨겨진 게시글입니다.")
                                .visibility(RecruitmentVisibility.HIDDEN)
                                .participating(participating)
                                .joinable(false)
                                .bookmarked(bookmarkedIds.contains(recruitment.getId()))
                                .build();
                    }

                    return RecruitmentResponseDto.from(recruitment, participating, bookmarkedIds.contains(recruitment.getId()));
                })
                .toList();

        return myParticipants;

    }

    // 잘못된 페이지 값과 과도한 조회 크기는 DB 조회 전에 거부
    private void validatePageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    @Transactional
    public RecruitmentParticipantRemoveResponseDto removeParticipant(
            Long userId,
            Long recruitmentId,
            RecruitmentParticipantRemoveRequestDto request
    ){
        applyLockTimeout();
        
        // 게시글 조회
        Recruitments recruitment = recruitmentsRepository.findByIdForUpdate(recruitmentId)
                .orElseThrow(()->new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND));

        // 게시글이 숨김 처리되어 있으면 거부
        if(recruitment.getVisibility() == RecruitmentVisibility.HIDDEN){
            throw new BusinessException(ErrorCode.RECRUITMENT_HIDDEN);
        }

        // HOST인지 검증
        if(!recruitment.getAuthor().getId().equals(userId)){
            throw new BusinessException(ErrorCode.NOT_RECRUITMENT_AUTHOR);
        }

        // 권한 확인 후 게시글 상태 검증
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        if ((recruitment.getStatus() != RecruitmentStatus.OPEN
                && recruitment.getStatus() != RecruitmentStatus.CLOSED)
                || !recruitment.getActivityAt().isAfter(now)) {
            throw new BusinessException(ErrorCode.INVALID_RECRUITMENT_STATUS_TRANSITION);
        }

        // 공개 ID를 내부 ID로 변환. 형식이 잘못되면 INVALID_INPUT(400)
        Long targetUserId = hashIdsUtils.decode(request.getTargetUserId());

        // 제외할 User의 참여 정보를 검색
        RecruitmentParticipants participant = recruitmentParticipantsRepository
                .findByRecruitment_IdAndUser_Id(recruitmentId, targetUserId)
                .orElseThrow(()->new BusinessException(ErrorCode.RECRUITMENT_PARTICIPANT_NOT_FOUND));


        // 제외할 User의 참여 상태를 검증
        if(participant.getStatus() != RecruitmentParticipantStatus.JOINED){
            throw new BusinessException(ErrorCode.RECRUITMENT_PARTICIPANT_NOT_ACTIVE);
        }


        // 사용자 참여 취소
        participant.remove(request.getRemovalReason(), now);
        recruitmentChatRooms.leave(recruitmentId, targetUserId, now);


        // 게시글 참여 인원 감소
        recruitment.decreaseParticipants(now);


        return RecruitmentParticipantRemoveResponseDto.from(
                participant,
                hashIdsUtils.encode(targetUserId)
        );
    }

    public RecruitmentParticipantsResponseDto getRecruitmentParticipants(
            Long userId,
            Long recruitmentId
    ){

        Recruitments recruitment = recruitmentsRepository.findById(recruitmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND));

        // 상세 조회와 같은 기준: 숨김 글은 작성자만 조회
        boolean mine = recruitment.getAuthor().getId().equals(userId);
        if (!mine && !recruitment.isVisible()) {
            throw new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND);
        }

        List<RecruitmentParticipantsResponseDto.Participant> participants = recruitmentParticipantsRepository
                .findParticipantRows(recruitmentId, RecruitmentParticipantStatus.JOINED)
                .stream()
                .map(row -> RecruitmentParticipantsResponseDto.Participant.builder()
                        .userId(hashIdsUtils.encode(row.getUserId()))
                        .nickname(row.getNickname())
                        .profileImageUrl(row.getProfileImageUrl())
                        .role(row.getRole())
                        .joinedAt(row.getJoinedAt())
                        .me(row.getUserId().equals(userId))
                        .build())
                .toList();

        return RecruitmentParticipantsResponseDto.builder()
                .recruitmentId(recruitment.getId())
                .nowParticipants(recruitment.getNowParticipants())
                .maxParticipants(recruitment.getMaxParticipants())
                .participants(participants)
                .build();


    }

}
