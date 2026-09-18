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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

    private record Candidate(
            MatchRequests existingMatchRequest,
            Long newUserId,
            double matchPercentage
    ) {
        static Candidate existing(MatchRequests matchRequest) {
            return new Candidate(
                    matchRequest,
                    null,
                    matchRequest.getMatchPercentage()
            );
        }

        static Candidate fresh(UserPreferencesRepository.RecommendationCandidate candidate) {
            return new Candidate(
                    null,
                    candidate.getUserId(),
                    candidate.getMatchPercentage()
            );
        }

        boolean isExisting() {
            return existingMatchRequest != null;
        }
    }

    @Transactional
    public void match(Long userId){

        // 가장 먼저 본인 preferences 잠금
        userPreferencesRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.USER_NOT_FOUND));

        Users me = usersRepository.findById(userId)
                .orElseThrow(()->new BusinessException(ErrorCode.USER_NOT_FOUND));

        if(me.getUserPreferences().getIsMatched()){
            throw new BusinessException(ErrorCode.ALREADY_CONFIRMED);
        }

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

        UserPreferences myPreference = userPreferencesRepository.findByUserIdWithUserDetails(userId)
                .orElseThrow(()->new BusinessException(ErrorCode.USER_NOT_FOUND));

        String gender = myPreference.getUser().getUserDetails().getGender();
        Integer smokingStatus = myPreference.getSmokingStatus();
        float[] vec = myPreference.getLifestyleVector();
        String vector = Arrays.toString(vec).replace(" ", "");


        List<MatchRequests> reusableMatches =  matchRepository.findReusableCandidatesWithSmoking(
                userId,
                smokingStatus,
                PageRequest.of(0,3)
        );

        List<UserPreferencesRepository.RecommendationCandidate> recommendationCandidates = userPreferencesRepository.findNewRecommendationCandidates(
                userId,
                gender,
                smokingStatus,
                vector,
                3
        );

        List<Candidate> candidates = new ArrayList<>();

        for(MatchRequests matchRequests : reusableMatches){
            candidates.add(Candidate.existing(matchRequests));
        }

        for(UserPreferencesRepository.RecommendationCandidate freshCandidate : recommendationCandidates){
            candidates.add(Candidate.fresh(freshCandidate));
        }

        List<Candidate> selected = candidates.stream()
                .sorted((a,b) -> Double.compare(b.matchPercentage(), a.matchPercentage()))
                .limit(3)
                .collect(Collectors.toCollection(ArrayList::new));

        int needed = 3 - selected.size();

        if(needed != 0){

            List<Long> selectedUserIds = selected.stream()
                    .map(candidate -> {
                        if(candidate.isExisting()){
                            MatchRequests matchRequests = candidate.existingMatchRequest();

                            return matchRequests.getUserLow().getId().equals(userId)
                                    ? matchRequests.getUserHigh().getId()
                                    : matchRequests.getUserLow().getId();
                        }

                        return candidate.newUserId();

                    })
                    .toList();

            List<MatchRequests> reusableMatchesIgnoreSmoking = matchRepository.findReusableCandidatesIgnoringSmoking(
                    userId,
                    PageRequest.of(0, selectedUserIds.size() + needed)
            );


            List<Long> excludedUserIds = selectedUserIds.isEmpty()
                    ? List.of(-1L)
                    : selectedUserIds;

            List<UserPreferencesRepository.RecommendationCandidate> recommendationCandidatesIgnoreSmoking = userPreferencesRepository.findNewRecommendationCandidatesIgnoringSmoking(
                    userId,
                    gender,
                    excludedUserIds,
                    vector,
                    needed
            );

            List<Candidate> candidates2 = new ArrayList<>();

            for (MatchRequests matchRequests : reusableMatchesIgnoreSmoking) {
                Long candidateUserId = matchRequests.getUserLow().getId().equals(userId)
                        ? matchRequests.getUserHigh().getId()
                        : matchRequests.getUserLow().getId();

                if (!selectedUserIds.contains(candidateUserId)) {
                    candidates2.add(Candidate.existing(matchRequests));
                }
            }

            for(UserPreferencesRepository.RecommendationCandidate freshCandidate : recommendationCandidatesIgnoreSmoking){
                candidates2.add(Candidate.fresh(freshCandidate));
            }

            selected.addAll(
                    candidates2.stream()
                            .sorted((a, b) -> Double.compare(b.matchPercentage(), a.matchPercentage()))
                            .limit(needed)
                            .toList()
            );
        }


        if(selected.isEmpty()){
            throw new BusinessException(ErrorCode.MATCH_CANDIDATE_NOT_FOUND);
        }


        for (Candidate candidate : selected) {
            if (candidate.isExisting()) {
                candidate.existingMatchRequest()
                        .updateStatusOf(userId, MatchStatus.RECOMMENDED);
            } else {
                MatchRequests newMatchRequest = createMatchRequest(
                        userId,
                        candidate.newUserId(),
                        candidate.matchPercentage()
                );

                try {
                    matchRepository.saveAndFlush(newMatchRequest);
                } catch (DataIntegrityViolationException e) {
                    throw new BusinessException(
                            ErrorCode.MATCH_ALREADY_REROLLED_TODAY,
                            "매칭이 겹쳤어요. 다시 시도해 주세요."
                    );
                }
            }
        }

        myPreference.updateIsRerolled();

    }


    private MatchRequests createMatchRequest(
            Long myUserId,
            Long otherUserId,
            Double matchPercentage
    ){
        Users me = usersRepository.findById(myUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Users other = usersRepository.findById(otherUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Users higher = (myUserId >= otherUserId)? me:other;
        Users lower = (myUserId >= otherUserId)? other:me;

        MatchRequests newMatchRequest = MatchRequests.builder()
                .userHigh(higher)
                .userLow(lower)
                .userHighPreferences(higher.getUserPreferences())
                .userLowPreferences(lower.getUserPreferences())
                .matchPercentage(matchPercentage)
                .userHighStatus(MatchStatus.NONE)
                .userLowStatus(MatchStatus.NONE)
                .build();

        newMatchRequest.updateStatusOf(myUserId, MatchStatus.RECOMMENDED);

        return newMatchRequest;
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
