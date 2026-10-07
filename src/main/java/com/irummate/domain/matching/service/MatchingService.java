package com.irummate.domain.matching.service;

import com.irummate.domain.chat.service.ChatService;
import com.irummate.domain.matching.dto.ConfirmedContactResponseDto;
import com.irummate.domain.matching.dto.MatchingResponseDto;
import com.irummate.domain.matching.dto.PreferredAnswerDto;
import com.irummate.domain.matching.entity.MatchRequests;
import com.irummate.domain.matching.entity.MatchStatus;
import com.irummate.domain.matching.repository.MatchRepository;
import com.irummate.domain.survey.entity.UserPreferences;
import com.irummate.domain.survey.repository.UserPreferencesRepository;
import com.irummate.domain.user.entity.UserDetails;
import com.irummate.domain.user.entity.UserRole;
import com.irummate.domain.user.entity.UserStatus;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.util.HashIdsUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static com.irummate.domain.matching.util.MatchingDtoMapper.toCardStatus;

@Slf4j
@Service
public class MatchingService {

    private final MatchRepository matchRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final UsersRepository usersRepository;
    private final ChatService chatService;
    private final HashIdsUtils hashIdsUtils;


    @Autowired
    public MatchingService(MatchRepository matchRepository,
                           UserPreferencesRepository userPreferencesRepository,
                           UsersRepository usersRepository,
                           ChatService chatService,
                           HashIdsUtils hashIdsUtils){
        this.matchRepository = matchRepository;
        this.userPreferencesRepository = userPreferencesRepository;
        this.usersRepository = usersRepository;
        this.chatService = chatService;
        this.hashIdsUtils = hashIdsUtils;
    }


    @Transactional
    public void confirm(Long userId, Long receiverId){

        if (userId.equals(receiverId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        List<Long> ids = List.of(userId, receiverId)
                .stream()
                .sorted()
                .toList();

        List<UserPreferences> lockedPrefs =
                userPreferencesRepository.findAllByIdsForUpdate(ids);

        if (lockedPrefs.size() != 2) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (lockedPrefs.stream()
                .anyMatch(pref -> Boolean.TRUE.equals(pref.getIsMatched()))) {
            throw new BusinessException(ErrorCode.ALREADY_CONFIRMED);
        }

        MatchRequests myMatchRequest = matchRepository.findByIds(userId, receiverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_REQUEST_NOT_FOUND));

        validateReceiverAvailable(myMatchRequest, receiverId, ErrorCode.NOT_CONFIRMABLE_STATUS);

        MatchStatus myStatus = myMatchRequest.getStatusOf(userId);
        MatchStatus otherStatus = myMatchRequest.getStatusOf(receiverId);

        if (myStatus == MatchStatus.CLOSED || otherStatus == MatchStatus.CLOSED) {
            throw new BusinessException(ErrorCode.NOT_CONFIRMABLE_STATUS);
        }

        if(
                (myStatus.equals(MatchStatus.HEART) && otherStatus.equals(MatchStatus.HEART))
                || (myStatus.equals(MatchStatus.HEART) && otherStatus.equals(MatchStatus.FINAL_CONFIRMED))
        ){
            myMatchRequest.updateStatusOf(userId, MatchStatus.FINAL_CONFIRMED);
            if(myMatchRequest.isConfirmed()){
                myMatchRequest.getUserHighPreferences().updateIsMatched();
                myMatchRequest.getUserLowPreferences().updateIsMatched();
                chatService.closeChatRoomsByUserIdExceptMatchRequestId(userId, myMatchRequest.getId());
                chatService.closeChatRoomsByUserIdExceptMatchRequestId(receiverId, myMatchRequest.getId());

                closeOtherMatchRequests(userId, myMatchRequest);
                closeOtherMatchRequests(receiverId, myMatchRequest);
            }

            return;
        }

        throw new BusinessException(ErrorCode.NOT_CONFIRMABLE_STATUS);
    }

    @Transactional(readOnly = true)
    public ConfirmedContactResponseDto getConfirmedContact(Long userId, Long receiverId) {
        if (userId.equals(receiverId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        MatchRequests matchRequest = matchRepository.findByIdsWithoutLock(userId, receiverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_REQUEST_NOT_FOUND));

        if (!matchRequest.isConfirmed()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "서로 최종 확정된 상대의 연락처만 조회할 수 있습니다.");
        }

        Users partner = matchRequest.getUserLow().getId().equals(userId)
                ? matchRequest.getUserHigh()
                : matchRequest.getUserLow();

        UserDetails partnerDetails = partner.getUserDetails();
        if (partnerDetails == null) {
            throw new BusinessException(ErrorCode.USER_DETAILS_REQUIRED);
        }

        return new ConfirmedContactResponseDto(
                partnerDetails.getRealName(),
                partnerDetails.getPhoneNumber()
        );
    }



    @Transactional
    public void heart(Long userId, Long receiverId){
        MatchRequests myMatchRequest = matchRepository.findByIds(userId, receiverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_REQUEST_NOT_FOUND));

        validateReceiverAvailable(myMatchRequest, receiverId, ErrorCode.NOT_HEARTABLE_STATUS);

        MatchStatus myStatus = myMatchRequest.getStatusOf(userId);
        MatchStatus otherStatus = myMatchRequest.getStatusOf(receiverId);


        if (myStatus == MatchStatus.CLOSED || otherStatus == MatchStatus.CLOSED) {
            throw new BusinessException(ErrorCode.NOT_HEARTABLE_STATUS);
        }


        if(
                (myStatus.equals(MatchStatus.RECOMMENDED) && otherStatus.equals(MatchStatus.NONE))
                || (myStatus.equals(MatchStatus.RECOMMENDED) && otherStatus.equals(MatchStatus.RECOMMENDED))
                || (myStatus.equals(MatchStatus.RECOMMENDED) && otherStatus.equals(MatchStatus.HEART))
                || (myStatus.equals(MatchStatus.NONE) && otherStatus.equals(MatchStatus.HEART))
        ){
            myMatchRequest.updateStatusOf(userId, MatchStatus.HEART);
            if(myMatchRequest.isHeartMatched()) {
                chatService.createChatRoomIfNotExists(myMatchRequest.getId());
            }
            return;
        }


        throw new BusinessException(ErrorCode.NOT_HEARTABLE_STATUS);

    }



    @Transactional
    public void reject(Long userId, Long receiverId){

        MatchRequests targetMatchRequest = matchRepository.findByIds(userId, receiverId)
                .orElseThrow(()->new BusinessException(ErrorCode.MATCH_REQUEST_NOT_FOUND));

        validateReceiverAvailable(targetMatchRequest, receiverId, ErrorCode.NOT_REJECTABLE_STATUS);

        MatchStatus myStatus = targetMatchRequest.getStatusOf(userId);
        MatchStatus otherStatus = targetMatchRequest.getStatusOf(receiverId);


        if (myStatus == MatchStatus.CLOSED || otherStatus == MatchStatus.CLOSED) {
            throw new BusinessException(ErrorCode.NOT_REJECTABLE_STATUS);
        }


        if(otherStatus == MatchStatus.REJECTED){
            throw new BusinessException(ErrorCode.NOT_REJECTABLE_STATUS);
        }

        if(myStatus != MatchStatus.HEART
                && myStatus != MatchStatus.RECOMMENDED
                && !(myStatus == MatchStatus.NONE && otherStatus == MatchStatus.HEART)){
            throw new BusinessException(ErrorCode.NOT_REJECTABLE_STATUS);
        }

        targetMatchRequest.updateStatusOf(userId, MatchStatus.REJECTED);
        chatService.closeChatRoomByMatchRequestId(targetMatchRequest.getId());
    }



    @Transactional(readOnly = true)
    public List<MatchingResponseDto> getMatchingStatus(Long userId){

        List<MatchRequests> myMatchRequests = matchRepository.findAllVisibleByUserId(userId);

        if(myMatchRequests.isEmpty()){
            return List.of();
        }

        List<MatchingResponseDto> matchingResponseDtos = new ArrayList<>();

        for(MatchRequests matchRequest : myMatchRequests){
            Users other = matchRequest.getUserHigh().getId().equals(userId)
                    ? matchRequest.getUserLow()
                    : matchRequest.getUserHigh();

            Users me = matchRequest.getUserLow().getId().equals(userId)
                    ? matchRequest.getUserLow()
                    : matchRequest.getUserHigh();


            LocalDateTime recommendedAt = matchRequest.getUserLow().getId().equals(userId)
                    ? matchRequest.getUserLowRecommendedAt()
                    : matchRequest.getUserHighRecommendedAt();

            MatchingResponseDto matchingResponseDto = MatchingResponseDto.builder()
                    .userId(hashIdsUtils.encode(other.getId()))
                    .name(other.getNickname())
                    .gender(other.getUserDetails().getGender())
                    .age(other.getUserDetails().getAge())
                    .introduce(other.getUserPreferences().getIntroduce())
                    .imageUrl(other.getProfileImageUrl())
                    .department(other.getUserDetails().getDepartment())
                    .matchPercentage(matchRequest.getMatchPercentage())
                    .matchStatus(matchRequest.getUserLow().equals(other)
                            ?toCardStatus(matchRequest.getUserHighStatus(),matchRequest.getUserLowStatus())
                            :toCardStatus(matchRequest.getUserLowStatus(),matchRequest.getUserHighStatus()))
                    .smokingStatus(other.getUserPreferences().getSmokingStatus())
                    .preferredAnswers(me.getUserPreferences().getVisibleProfileFields().stream()
                            .distinct()
                            .limit(3)
                            .map(field -> PreferredAnswerDto.builder()
                                    .field(field)
                                    .value(field.getValueFrom(other.getUserPreferences().getAnswers()))
                                    .build())
                            .toList()
                    )
                    .matchDate(recommendedAt)
                    .build();

            matchingResponseDtos.add(matchingResponseDto);
        }

        return matchingResponseDtos;
    }


    @Transactional
    public void match(Long userId){

        // 가장 먼저 본인 preferences 잠금
        UserPreferences myPreference;
        // 잠금 실패 시 대기 없이 바로 예외
        // 연속 매칭 시도에 빠르게 대응
        try {
            myPreference =
                    userPreferencesRepository.findByUserIdForUpdate(userId)
                            .orElseThrow(() ->
                                    new BusinessException(ErrorCode.USER_NOT_FOUND));
        } catch (PessimisticLockingFailureException e) {
            throw new BusinessException(
                    ErrorCode.MATCH_IN_PROGRESS
            );
        }


        // User 데이터
        Users me = myPreference.getUser();


        // 이미 최종확정을 지은 상태인지 확인
        if(myPreference.getIsMatched()){
            throw new BusinessException(ErrorCode.ALREADY_CONFIRMED);
        }

        // 오늘 이미 매칭을 돌렸는지 확인
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime startOfTomorrow = today.plusDays(1).atStartOfDay();

        LocalDateTime myRerolledAt = me.getUserPreferences().getRerolledAt();

        boolean isRerolledToday = myRerolledAt != null
                && !myRerolledAt.isBefore(startOfToday)
                && myRerolledAt.isBefore(startOfTomorrow);

        if (isRerolledToday) {
            throw new BusinessException(ErrorCode.MATCH_ALREADY_REROLLED_TODAY);
        }



        // 사용자에게 추천되지 않은 Match 관계 조회
        // 높은 매칭 점수의 사용자가 후보자가 되어있을 가능성이 높으므로
        // 후보자들을 우선적으로 고려
        // 너무 많은 후보자를 가져오지 않도록 최대 30명까지 고정
        List<MatchRequests> candidates =
                matchRepository.findAvailableCandidates(
                        userId,
                        PageRequest.of(0, 30)
                );


        // 만약 후보자가 10명보다 적으면 후보자를 추가(10명 추가)
        // 흡연 조건이 걸린 후보자 탐색
        if(candidates.size() < 10){

            // L2 거리 후보자 탐색을 위한 기본 정보 세팅
            String gender = me.getUserDetails().getGender();
            Integer smokingStatus = myPreference.getSmokingStatus();
            float[] vec = myPreference.getLifestyleVector();
            String vector = Arrays.toString(vec).replace(" ", "");

            // 후보자 탐색
            // L2 점수까지 계산(보정 50 + L2 점수 40)
            List<UserPreferencesRepository.RecommendationCandidate> recommendationCandidates = userPreferencesRepository.findNewRecommendationCandidates(
                userId,
                gender,
                smokingStatus,
                vector,
                10
            );


            // 추가할 MatchRequest
            // 점수까지 계산된 후보자들
            // L2 거리로 가져온 후보자들의 점수에 가중치 부여 후 MatchRequest로 변환하는 createCandidates 함수 호출
            List<MatchRequests> newMatchRequests =
                    createCandidates(me, recommendationCandidates);


            // 후보자가 추가되었으면 저장 및 후보자 재탐색 수행
            if(!newMatchRequests.isEmpty()) {

                // 추가된 후보자들을 저장
                // 중복 예외 방지를 위한 코드
                for (MatchRequests newMatchRequest : newMatchRequests) {
                    matchRepository.insertCandidateIfAbsent(
                            newMatchRequest.getUserLow().getId(),
                            newMatchRequest.getUserHigh().getId(),
                            newMatchRequest.getUserLowPreferences().getUserId(),
                            newMatchRequest.getUserHighPreferences().getUserId(),
                            newMatchRequest.getMatchPercentage()
                    );
                }


                // 후보자들을 다시 정렬해 탐색
                candidates =
                        matchRepository.findAvailableCandidates(
                                userId,
                                PageRequest.of(0, 30)
                        );
            }

        }

        // 흡연 조건을 걸고 후보자 탐색을 진행했음에도 후보자가 10명이 넘지 않으면
        // 흡연 조건을 완화시키고 후보자 탐색
        if(candidates.size() < 10){
            // L2 거리 후보자 탐색을 위한 기본 정보 세팅
            String gender = me.getUserDetails().getGender();
            float[] vec = myPreference.getLifestyleVector();
            String vector = Arrays.toString(vec).replace(" ", "");

            // 후보자 탐색
            // L2 점수까지 계산(보정 50 + L2 점수 40)
            List<UserPreferencesRepository.RecommendationCandidate> recommendationCandidates = userPreferencesRepository.findNewRecommendationCandidatesIgnoringSmoking(
                    userId,
                    gender,
                    vector,
                    10
            );


            // 추가할 MatchRequest
            // 점수까지 계산된 후보자들
            // L2 거리로 가져온 후보자들의 점수에 가중치 부여 후 MatchRequest로 변환하는 createCandidates 함수 호출
            List<MatchRequests> newMatchRequests =
                    createCandidates(me, recommendationCandidates);



            // 후보자가 추가되었으면 저장 및 후보자 재탐색 수행
            if(!newMatchRequests.isEmpty()) {

                // 추가된 후보자들을 저장
                // 중복 예외 방지를 위한 코드
                for (MatchRequests newMatchRequest : newMatchRequests) {
                    matchRepository.insertCandidateIfAbsent(
                            newMatchRequest.getUserLow().getId(),
                            newMatchRequest.getUserHigh().getId(),
                            newMatchRequest.getUserLowPreferences().getUserId(),
                            newMatchRequest.getUserHighPreferences().getUserId(),
                            newMatchRequest.getMatchPercentage()
                    );
                }


                // 후보자들을 다시 정렬해 탐색
                candidates =
                        matchRepository.findAvailableCandidates(
                                userId,
                                PageRequest.of(0, 30)
                        );
            }

        }

        // 추천해줄 후보자가 1명도 없으면 예외 발생
        // 한명이라도 존재하면 추천
        if(candidates.size() <= 0) {
            throw new BusinessException(ErrorCode.MATCH_CANDIDATE_NOT_FOUND);
        }


        // 후보자들 중 최상위 후보자 3명을 추천
        // 후보자 리스트들 중 다양한 상황(동시 요청, 추천 update 실패 등)을 고려하여
        // 3명을 확보할 때까지 반복(변수 상황으로 인해 최상위 후보자 3명이 아닐 수도 있음)
        int recommendedCount = 0;

        for (MatchRequests candidate : candidates) {
            if (recommendedCount >= 3) {
                break;
            }

            int updatedCount =
                    recommendCandidateIfAvailable(candidate, userId);

            // 후보자 업데이트를 실패하면,
            // 추천 후보자 수를 늘리지 않고 continue
            if (updatedCount == 0) {
                // 다른 요청으로 상태가 변경된 후보
                continue;
            }

            recommendedCount++;
        }


        // 추천할 후보가 하나도 없으면 예외 발생
        // 후보자가 있었어도 동시 요청 등의 문제로 인해
        // 후보자 상태를 업데이트 하지 못했을 때 발생
        if (recommendedCount == 0) {
            throw new BusinessException(
                    ErrorCode.MATCH_CANDIDATE_NOT_FOUND
            );
        }


        // 오늘 매칭을 돌렸음을 update
        myPreference.updateIsRerolled();

    }

    // L2로 뽑아온 후보자들을 MatchRequest에 저장시키기 위한 함수
    // 가중치(나 5점, 상대방 5점)를 부여하며 계산하여 None-None 관계로 저장
    private List<MatchRequests> createCandidates(
            Users me,
            List<UserPreferencesRepository.RecommendationCandidate> recommendationCandidates
    ){

        // 먼저 후보자 리스트가 비어있는지 확인
        if(recommendationCandidates.isEmpty()){
            return List.of();
        }


        // 후보자들의 userId를 필터링
        List<Long> candidateUserIds = recommendationCandidates.stream()
                .map(UserPreferencesRepository.RecommendationCandidate :: getUserId)
                .toList();


        // 상대방 Users와 UserPreferences를 모두 fetch
        Map<Long, Users> usersById = usersRepository.findAllByIdsWithPreferences(candidateUserIds)
                .stream()
                .collect(Collectors.toMap(
                        Users::getId,
                        user->user
                ));

        // 반환용 빈 List 생성
        List<MatchRequests> newMatchRequests = new ArrayList<>();

        for(UserPreferencesRepository.RecommendationCandidate recommendationCandidate : recommendationCandidates){

            // 후보자를 한명씩 꺼냄
            Users other = usersById.get(recommendationCandidate.getUserId());

            if(other == null){
                continue;
            }

            // 가중치 부여를 위한 매칭 점수 변수
            Double matchPercentage = recommendationCandidate.getMatchPercentage();


            // 흡연 조건 완화 시
            // 흡연 여부가 일치하지 않는지 확인
            boolean smokingMismatch = !Objects.equals(
                    me.getUserPreferences().getSmokingStatus(),
                    other.getUserPreferences().getSmokingStatus()
            );

            // 흡연 여부가 일치하지 않으면
            // 2점 감점
            if(smokingMismatch){
                 matchPercentage -= 2.0;
            }

            // MatchRequest 저장을 위한 ID 대소 구분
            Users higher = (me.getId() >= other.getId())? me:other;
            Users lower = (me.getId() >= other.getId())? other:me;


            // lower가 중요하게 생각하는 항목 일치 여부 확인
            long lowUserImportantFieldMatchCount = lower.getUserPreferences().getVisibleProfileFields().stream()
                    .distinct()
                    .limit(3)
                    .filter(field -> {
                        Integer lowerValue =
                                field.getValueFrom(lower.getUserPreferences().getAnswers());

                        Integer higherValue =
                                field.getValueFrom(higher.getUserPreferences().getAnswers());

                        return Objects.equals(lowerValue, higherValue);
                    })
                    .count();


            // 항목 일치 개수에 따른 가중치 부여
            switch ((int) lowUserImportantFieldMatchCount){
                case 1: matchPercentage += 1.5; break;
                case 2: matchPercentage += 3.0; break;
                case 3: matchPercentage += 5.0; break;
                default: break;
            }


            // higher가 중요하게 생각하는 항목 일치 여부 확인
            long highUserImportantFieldMatchCount = higher.getUserPreferences().getVisibleProfileFields().stream()
                    .distinct()
                    .limit(3)
                    .filter(field -> {
                        Integer lowerValue =
                                field.getValueFrom(lower.getUserPreferences().getAnswers());

                        Integer higherValue =
                                field.getValueFrom(higher.getUserPreferences().getAnswers());

                        return Objects.equals(lowerValue, higherValue);
                    })
                    .count();

            // 항목 일치 개수에 따른 가중치 부여
            switch ((int) highUserImportantFieldMatchCount){
                case 1: matchPercentage += 1.5; break;
                case 2: matchPercentage += 3.0; break;
                case 3: matchPercentage += 5.0; break;
                default: break;
            }


            newMatchRequests.add(MatchRequests.builder()
                    .userHigh(higher)
                    .userLow(lower)
                    .userHighPreferences(higher.getUserPreferences())
                    .userLowPreferences(lower.getUserPreferences())
                    .matchPercentage(matchPercentage)
                    .userHighStatus(MatchStatus.NONE)
                    .userLowStatus(MatchStatus.NONE)
                    .build()
            );

        }

        return newMatchRequests;
    }


    /**
     * 역할: match request 상대방(receiver)이 여전히 매칭 가능한 유저인지 검증합니다.
     * 상대가 정지/탈퇴 등으로 ACTIVE·USER가 아니면 액션을 막습니다. (상태 변화에 대한 방어)
     */
    private void validateReceiverAvailable(MatchRequests matchRequest, Long receiverId, ErrorCode errorCode) {
        Users receiver = matchRequest.getUserLow().getId().equals(receiverId)
                ? matchRequest.getUserLow()
                : matchRequest.getUserHigh();

        if (receiver.getStatus() != UserStatus.ACTIVE || receiver.getRole() != UserRole.USER) {
            throw new BusinessException(errorCode);
        }
    }


    private void closeOtherMatchRequests(Long userId, MatchRequests confirmedMatchRequest){
        List<MatchRequests> myOtherMatchRequests = matchRepository.findAllByUserIdExceptConfirmed(userId, confirmedMatchRequest.getId());

        for(MatchRequests matchRequest : myOtherMatchRequests){
            matchRequest.updateStatusOf(userId, MatchStatus.CLOSED);
        }
    }


    // 추천을 위한 update 쿼리
    // 성공 실패 여부를 반환
    private int recommendCandidateIfAvailable(
            MatchRequests candidate,
            Long userId
    ) {
        LocalDateTime recommendedAt =
                LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        return matchRepository.recommendCandidateIfAvailable(
                candidate.getId(),
                userId,
                recommendedAt
        );
    }


    /**
     * 역할: 특정 유저(정지/탈퇴 등)와 연관된 진행 중 match request를 CLOSED 처리합니다.
     * 해당 유저 쪽 상태만 CLOSED로 변경하며, findAllVisibleByUserId가 한쪽만 CLOSED여도
     * 목록에서 제외하므로 상대방 화면에서도 더 이상 노출되지 않습니다.
     * 단, 이미 최종 확정(FINAL_CONFIRMED)된 매칭은 건드리지 않습니다.
     * 확정 관계를 유지해 남은 상대방이 연락처 조회 불가 + 재매칭 불가로 고착되는 것을 방지합니다.
     */
    @Transactional
    public void closeAllMatchRequestsByUserId(Long userId){
        chatService.closeChatRoomsByUserId(userId);

        List<MatchRequests> myMatchRequests = matchRepository.findAllByUserId(userId);

        for(MatchRequests matchRequest : myMatchRequests){
            if(matchRequest.isConfirmed()){
                continue;
            }
            if(matchRequest.getStatusOf(userId) != MatchStatus.CLOSED){
                matchRequest.updateStatusOf(userId, MatchStatus.CLOSED);
            }
        }
    }


}
