package com.irummate.domain.recruitment.service;

import com.irummate.domain.recruitment.dto.*;
import com.irummate.domain.recruitment.entity.*;
import com.irummate.domain.recruitment.repository.RecruitmentParticipantsRepository;
import com.irummate.domain.recruitment.repository.RecruitmentsRepository;
import com.irummate.domain.recruitment.repository.RecruitmentBookmarksRepository;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
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

    private static final int MAX_PAGE_SIZE = 50;

    private final RecruitmentsRepository recruitmentsRepository;
    private final RecruitmentParticipantsRepository recruitmentParticipantsRepository;
    private final UsersRepository usersRepository;
    private final RecruitmentBookmarksRepository recruitmentBookmarksRepository;

    @PersistenceContext
    private EntityManager entityManager;


    // 모든 DB 상태 update 간 적용
    // 현재 요청의 트랜잭션에서만 잠금 대기를 1.5초로 제한
    private void applyLockTimeout() {
        entityManager.createNativeQuery("SET LOCAL lock_timeout = '1500ms'")
                .executeUpdate();
    }


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



        // 숨김 처리된 글인지 확인
        // 숨김 처리된 글은 사용자에게 공개되지 않으므로 숨김 처리
        // 단, 작성자는 조회 가능
        if(!mine && recruitment.getVisibility() == RecruitmentVisibility.HIDDEN){
            throw new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND);
        }

        // 로그인 사용자 본인의 참여 기록만 조회
        RecruitmentParticipants myParticipation = recruitmentParticipantsRepository
                .findByRecruitment_IdAndUser_Id(recruitmentId, userId)
                .orElse(null);

        // Dto로 변환해 반환
        return RecruitmentDetailResponseDto.from(
                recruitment,
                RecruitmentParticipationView.from(
                        mine, myParticipation == null ? null : myParticipation.getStatus()
                ),
                myParticipation == null ? null : myParticipation.getRemovalReason(),
                recruitmentBookmarksRepository.existsByUser_IdAndRecruitment_Id(userId, recruitmentId)
        );


    }


    @Transactional(readOnly = true)
    public RecruitmentListResponseDto getRecruitments(
            Long userId,
            RecruitmentCategory category,
            RecruitmentSort sort,
            LocalDateTime cursorCreatedAt,
            LocalDateTime cursorDeadlineAt,
            Long cursorId,
            int size
    ){
        validateCursorRequest(sort, cursorCreatedAt, cursorDeadlineAt, cursorId, size);

        // 다음 목록 유무 확인용 1건만 추가 조회. 오프셋은 항상 0으로 유지
        Pageable pageable = PageRequest.of(0, size + 1);

        List<RecruitmentsRepository.RecruitmentListRow> rows;
        if (sort == RecruitmentSort.CLOSING_SOON) {
            // 한 요청 안에서는 같은 시각을 기준으로 마감 여부와 24시간 범위를 판단
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
            rows = recruitmentsRepository.findClosingSoonRecruitmentList(
                    userId, category, RecruitmentVisibility.VISIBLE, RecruitmentStatus.OPEN,
                    now, now.plusHours(24), cursorDeadlineAt, cursorId, pageable);
        } else {
            rows = recruitmentsRepository.findRecruitmentList(
                    userId, category, RecruitmentVisibility.VISIBLE, cursorCreatedAt, cursorId, pageable);
        }

        boolean hasNext = rows.size() > size;
        List<RecruitmentsRepository.RecruitmentListRow> selectedRows = hasNext ? rows.subList(0, size) : rows;
        List<RecruitmentResponseDto> items = selectedRows.stream()
                .map(row -> RecruitmentResponseDto.from(
                        row.getRecruitment(),
                        RecruitmentParticipationView.from(
                                userId.equals(row.getAuthorId()),
                                row.getParticipantStatus()
                        ),
                        row.getAuthorId(), row.getAuthorNickname(), row.getAuthorProfileImageUrl(), row.isBookmarked()
                ))
                .toList();

        // 확인용 추가 행이 아닌, 실제 반환한 마지막 행을 다음 커서로 사용
        RecruitmentCursorDto nextCursor = hasNext
                ? RecruitmentCursorDto.from(selectedRows.getLast().getRecruitment(), sort) : null;

        return RecruitmentListResponseDto.builder()
                .items(items)
                .hasNext(hasNext)
                .nextCursor(nextCursor)
                .build();
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
        Recruitments recruitment = Recruitments.builder()
                .author(author)
                .category(request.getCategory())
                .activityAt(request.getActivityAt())
                .deadlineAt(request.getDeadlineAt())
                .maxParticipants(includeHostInCapacity(request.getMaxParticipants()))
                .status(RecruitmentStatus.OPEN)
                .visibility(RecruitmentVisibility.VISIBLE)
                .nowParticipants(1)
                .build();
        recruitment.configureText(request.getTitle(), request.getPlace());
        recruitment.configurePrice(request.getPriceType(), request.getAmount());
        return recruitment;
    }


    // 정렬별 시각과 ID를 함께 전달. 정렬이 다른 커서를 섞으면 거부
    private void validateCursorRequest(RecruitmentSort sort, LocalDateTime cursorCreatedAt,
                                       LocalDateTime cursorDeadlineAt, Long cursorId, int size) {
        LocalDateTime cursorTime = sort == RecruitmentSort.CLOSING_SOON ? cursorDeadlineAt : cursorCreatedAt;
        if (sort == null || size < 1 || size > MAX_PAGE_SIZE
                || (sort == RecruitmentSort.LATEST && cursorDeadlineAt != null)
                || (sort == RecruitmentSort.CLOSING_SOON && cursorCreatedAt != null)
                || (cursorTime == null) != (cursorId == null)
                || (cursorId != null && cursorId <= 0)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
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
        applyLockTimeout();

        // 게시글 조회
        Recruitments recruitment = recruitmentsRepository.findByIdForUpdate(recruitmentId)
                .orElseThrow(()-> new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND));


        // 게시글이 숨김 처리되어 있으면 거부
        if(recruitment.getVisibility() == RecruitmentVisibility.HIDDEN){
            throw new BusinessException(ErrorCode.RECRUITMENT_HIDDEN);
        }


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
                request.getPlace(),
                request.getActivityAt(),
                request.getDeadlineAt(),
                request.getMaxParticipants() == null
                        ? null : includeHostInCapacity(request.getMaxParticipants()),
                request.getPriceType(),
                request.getAmount(),
                LocalDateTime.now(ZoneId.of("Asia/Seoul"))
        );

        recruitmentsRepository.flush();

        return RecruitmentDetailResponseDto.from(
                recruitment,
                RecruitmentParticipationView.MINE,
                null,
                recruitmentBookmarksRepository.existsByUser_IdAndRecruitment_Id(userId, recruitmentId)
                );
    }


    @Transactional
    public RecruitmentDetailResponseDto closeRecruitment(
            Long userId,
            Long recruitmentId
    ){
        applyLockTimeout();

        // 게시글 조회
        Recruitments recruitment = recruitmentsRepository.findByIdForUpdate(recruitmentId)
                .orElseThrow(()-> new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND));


        // 게시글이 숨김 처리되어 있으면 거부
        if(recruitment.getVisibility() == RecruitmentVisibility.HIDDEN){
            throw new BusinessException(ErrorCode.RECRUITMENT_HIDDEN);
        }



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
                RecruitmentParticipationView.MINE,
                null,
                recruitmentBookmarksRepository.existsByUser_IdAndRecruitment_Id(userId, recruitmentId)
        );

    }


    @Transactional
    public RecruitmentDetailResponseDto reopenRecruitment(
            Long userId,
            Long recruitmentId
    ){
        applyLockTimeout();

        // recruitment 조회
        Recruitments recruitment = recruitmentsRepository.findByIdForUpdate(recruitmentId)
                .orElseThrow(()->new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND));

        // 게시글이 숨김 처리되어 있으면 거부
        if(recruitment.getVisibility() == RecruitmentVisibility.HIDDEN){
            throw new BusinessException(ErrorCode.RECRUITMENT_HIDDEN);
        }


        // user가 게시글의 author인지 확인
        if(!recruitment.getAuthor().getId().equals(userId)){
            throw new BusinessException(ErrorCode.NOT_RECRUITMENT_AUTHOR);
        }


        // 게시글 모집 재개
        recruitment.reopen(LocalDateTime.now(ZoneId.of("Asia/Seoul")));

        recruitmentsRepository.flush();

        return RecruitmentDetailResponseDto.from(
                recruitment,
                RecruitmentParticipationView.MINE,
                null,
                recruitmentBookmarksRepository.existsByUser_IdAndRecruitment_Id(userId, recruitmentId)
        );
    }



    @Transactional
    public RecruitmentDetailResponseDto cancelRecruitment(
            Long userId,
            Long recruitmentId,
            RecruitmentCancelRequestDto request
    ){
        applyLockTimeout();

        // 게시글 조회
        Recruitments recruitment = recruitmentsRepository.findByIdForUpdate(recruitmentId)
                .orElseThrow(()->new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND));


        // 게시글이 숨김 처리되어 있으면 거부
        if(recruitment.getVisibility() == RecruitmentVisibility.HIDDEN){
            throw new BusinessException(ErrorCode.RECRUITMENT_HIDDEN);
        }


        // user가 게시글의 author인지 확인
        if(!recruitment.getAuthor().getId().equals(userId)){
            throw new BusinessException(ErrorCode.NOT_RECRUITMENT_AUTHOR);
        }

        // 취소
        recruitment.cancel(request.getCancelReason(), LocalDateTime.now(ZoneId.of("Asia/Seoul")));

        recruitmentsRepository.flush();

        return RecruitmentDetailResponseDto.from(
                recruitment,
                RecruitmentParticipationView.MINE,
                null,
                recruitmentBookmarksRepository.existsByUser_IdAndRecruitment_Id(userId, recruitmentId)
        );
    }

    @Transactional
    public RecruitmentDetailResponseDto endRecruitment(
            Long userId,
            Long recruitmentId
    ){
        applyLockTimeout();

        // 게시글 조회
        Recruitments recruitment = recruitmentsRepository.findByIdForUpdate(recruitmentId)
                .orElseThrow(()->new BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND));


        // 게시글이 숨김 처리되어 있으면 거부
        if(recruitment.getVisibility() == RecruitmentVisibility.HIDDEN){
            throw new BusinessException(ErrorCode.RECRUITMENT_HIDDEN);
        }



        // user가 게시글의 author인지 확인
        if(!recruitment.getAuthor().getId().equals(userId)){
            throw new BusinessException(ErrorCode.NOT_RECRUITMENT_AUTHOR);
        }


        // 활동 시작 이후 게시판 처리 종료
        recruitment.end(LocalDateTime.now(ZoneId.of("Asia/Seoul")));

        recruitmentsRepository.flush();

        return RecruitmentDetailResponseDto.from(
                recruitment,
                RecruitmentParticipationView.MINE,
                null,
                recruitmentBookmarksRepository.existsByUser_IdAndRecruitment_Id(userId, recruitmentId)
        );

    }



}
