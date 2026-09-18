package com.irummate.domain.user.service;

import com.irummate.domain.user.dto.UserDetailsRequestDto;
import com.irummate.domain.user.dto.UserDetailsResponseDto;
import com.irummate.domain.user.dto.UserDetailsUpdateRequestDto;
import com.irummate.domain.user.dto.UserProfileResponseDto;
import com.irummate.domain.user.dto.UserProfileUpdateRequestDto;
import com.irummate.domain.user.dto.UserProfileUpdateResponseDto;
import com.irummate.domain.user.entity.UserDetails;
import com.irummate.domain.user.entity.UserRole;
import com.irummate.domain.user.entity.UserStatus;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UserDetailsRepository;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.domain.matching.service.MatchingService;
import com.irummate.global.config.KakaoProperties;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * @Service: 스프링에게 이 클래스가 비즈니스 로직을 담당하는 '서비스' 컴포넌트임을 알립니다.
 * @RequiredArgsConstructor: final 변수인 두 개의 레포지토리(DB 창구)를 자동으로 주입받습니다.
 * @Transactional(readOnly = true): 데이터베이스를 '읽기 전용'으로 전제합니다.
 * 단순 조회 시 성능이 더 빨라지는 효과가 있습니다. (값을 바꿀 때만 메서드 위에 따로 @Transactional을 붙임)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserDetailsService {


    private final UsersRepository usersRepository;
    private final UserDetailsRepository userDetailsRepository;
    private final KakaoProperties kakaoProperties;
    private final MatchingService matchingService;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 역할: 사용자의 추가 필수 정보(본명, 학번, 나이 등)를 최초로 저장합니다.
     * @Transactional: 값의 수정/생성이 일어나므로, 도중에 에러가 나면 전부 취소되도록 안전장치를 켭니다.
     */
    @Transactional
    public UserDetailsResponseDto createDetails(Long userId, UserDetailsRequestDto request) {
        Long userPk = Long.valueOf(userId);
        // 이미 상세 정보가 등록된 유저인지 검사합니다.
        if (userDetailsRepository.existsById(userPk)) {
            // 이미 존재한다면 예외(409 Conflict)를 발생시킵니다.
            throw new BusinessException(ErrorCode.DETAILS_ALREADY_EXISTS);
        }

        // 토큰에서 꺼낸 userId가 실제로 가입된 유저인지 확인합니다.
        Users user = usersRepository.findById(userPk)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)); // 없으면 404 에러

        //입력받은 데이터(Dto)를 바탕으로 실제 DB 테이블 모양새의 객체(Entity)를 조립합니다.
        UserDetails userDetails = UserDetails.builder()
                .user(user)
                .realName(request.getRealName())
                .studentId(request.getStudentId())
                .age(request.getAge())
                .gender(request.getGender())
                .department(request.getDepartment())
                .phoneNumber(request.getPhoneNumber())
                .build();

        // [DB 저장] 조립된 객체를 데이터베이스에 저장(Insert)합니다.
        UserDetails savedDetails = userDetailsRepository.save(userDetails);
        if(user.getRole() == UserRole.GUEST){
            user.promoteToUser();
        }

        return toResponse(savedDetails);
    }

    /**
     * 유저 본인의 통합 프로필 정보를 조회합니다
     */
    @Transactional
    public UserDetailsResponseDto updateDetails(Long userId, UserDetailsUpdateRequestDto request) {
        Long userPk = Long.valueOf(userId);

        UserDetails userDetails = userDetailsRepository.findById(userPk)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        userDetails.update(
                request.getRealName(),
                request.getStudentId(),
                request.getAge(),
                request.getGender(),
                request.getDepartment(),
                request.getPhoneNumber()
        );

        return toResponse(userDetails);
    }

    public UserProfileResponseDto getProfile(Long userId) {
        Long userPk = Long.valueOf(userId);
        // 1. 기본 유저 정보(이메일, 닉네임 등)를 DB에서 찾습니다.
        Users user = usersRepository.findById(userPk)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 추가 상세 정보(학번, 학과 등)도 DB에서 찾습니다.
        // 필수 정보를 아직 입력 안 했을 수도 있으므로, 여기선 에러를 내지 않고 'null'이 되도록 .orElse(null) 처리를 합니다.
        UserDetails userDetails = userDetailsRepository.findById(userPk).orElse(null);

        // 3. 두 테이블에서 긁어온 정보를 (UserProfileResponseDto)에 합쳐서 담아 반환합니다.
        return UserProfileResponseDto.builder()
                .nickname(user.getNickname())
                .email(user.getEmail())
                .profileImageUrl(user.getProfileImageUrl())
                .role(user.getRole().name())
                .status(user.getStatus().name())
                .detail(toProfileDetail(userDetails)) // 상세 정보가 없으면 내부는 null로 담김
                .build();
    }

    /**
     * 역할: 유저의 프로필(닉네임, 프로필 사진)을 수정합니다.
     * @Transactional: DB 내 데이터의 실제 변경(Update)이 일어나므로 트랜잭션을 켭니다.
     */
    @Transactional
    public UserProfileUpdateResponseDto updateProfile(Long userId, UserProfileUpdateRequestDto request) {
        Long userPk = Long.valueOf(userId);
        // 1. 수정할 유저를 DB에서 찾습니다.
        Users user = usersRepository.findById(userPk)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 더티 체킹: 엔티티 객체의 값을 바꿔줍니다.
        // 스프링은 트랜잭션 안에서 객체의 값이 바뀌면, 메서드가 끝날 때 자동으로 DB에 UPDATE 쿼리를 날려줍니다.
        user.updateProfile(request.getNickname(), request.getProfileImageUrl());

        // 3. 수정 완료된 결과를 가방에 담아 돌려줍니다.
        return UserProfileUpdateResponseDto.builder()
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .build();
    }

    @Transactional
    public void withdraw(Long userId) {
        Long userPk = Long.valueOf(userId);

        Users user = usersRepository.findById(userPk)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "이미 탈퇴한 계정입니다.");
        }

        unlinkKakaoUser(user.getOauthId());
        user.withdraw();

        // 탈퇴한 계정과 연관된 모든 match request를 CLOSED 처리합니다.
        matchingService.closeAllMatchRequestsByUserId(userPk);
    }



    /**
     * 역할: DB용 객체(UserDetails)를 화면 반환용 가방(UserDetailsResponseDto)으로 변환해줍니다.
     */
    private UserDetailsResponseDto toResponse(UserDetails userDetails) {
        return UserDetailsResponseDto.builder()
                .realName(userDetails.getRealName())
                .studentId(userDetails.getStudentId())
                .age(userDetails.getAge())
                .gender(userDetails.getGender())
                .department(userDetails.getDepartment())
                .phoneNumber(userDetails.getPhoneNumber())
                .build();
    }

    /**
     * 역할: 프로필 통합 조회 시 내부에 쏙 들어갈 상세 정보 Dto를 생성해 줍니다. (정보가 없으면 안전하게 null 반환)
     */
    private UserProfileResponseDto.Detail toProfileDetail(UserDetails userDetails) {
        if (userDetails == null) {
            return null;
        }

        return UserProfileResponseDto.Detail.builder()
                .realName(userDetails.getRealName())
                .studentId(userDetails.getStudentId())
                .age(userDetails.getAge())
                .gender(userDetails.getGender())
                .department(userDetails.getDepartment())
                .phoneNumber(userDetails.getPhoneNumber())
                .build();
    }

    private void unlinkKakaoUser(String kakaoUserId) {
        String adminKey = kakaoProperties.getAdminKey();
        if (adminKey == null || adminKey.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "카카오 관리자 키가 필요합니다.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", "KakaoAK " + adminKey);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("target_id_type", "user_id");
        params.add("target_id", kakaoUserId);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            restTemplate.postForObject("https://kapi.kakao.com/v1/user/unlink", request, String.class);
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.KAKAO_API_ERROR);
        }
    }
}
