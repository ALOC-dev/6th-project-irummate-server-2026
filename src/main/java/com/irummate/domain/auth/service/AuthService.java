package com.irummate.domain.auth.service;

import com.irummate.domain.auth.dto.AuthStatusResponseDto;
import com.irummate.domain.auth.dto.KakaoTokenResponseDto;
import com.irummate.domain.auth.dto.KakaoUserInfoResponseDto;
import com.irummate.domain.auth.dto.LoginResponseDto;
import com.irummate.domain.auth.dto.RefreshTokenResponseDto;
import com.irummate.domain.certification.entity.Certification;
import com.irummate.domain.certification.repository.CertificationRepository;
import com.irummate.domain.survey.repository.UserPreferencesRepository;
import com.irummate.domain.user.entity.UserStatus;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.config.KakaoProperties;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.jwt.JwtTokenProvider;
import com.irummate.global.util.HashIdsUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final KakaoProperties kakaoProperties;
    private final UsersRepository usersRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final CertificationRepository certificationRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final HashIdsUtils hashIdsUtils;

    private final RestTemplate restTemplate = new RestTemplate();

    @Transactional
    public LoginResult loginOrRegister(String code) {
        KakaoTokenResponseDto tokenResponse = getKakaoToken(code);
        KakaoUserInfoResponseDto userInfo = getKakaoUserInfo(tokenResponse.getAccessToken());

        UserRegistration userRegistration = findOrCreateUser(userInfo);
        Users user = userRegistration.user();
        Long internalUserId = user.getId();
        String encodedUserId = hashIdsUtils.encode(internalUserId);

        String accessToken = jwtTokenProvider.createAccessToken(encodedUserId, user.getRole().name());
        String refreshToken = jwtTokenProvider.createRefreshToken(encodedUserId, user.getRole().name());

        LoginResponseDto response = LoginResponseDto.builder()
                .accessToken(accessToken)
                .isNewUser(userRegistration.isNewUser())
                .user(LoginResponseDto.UserInfo.builder()
                        .id(encodedUserId)
                        .nickname(user.getNickname())
                        .role(user.getRole().name())
                        .status(user.getStatus().name())
                        .build())
                .build();

        return new LoginResult(response, refreshToken);
    }

    public RefreshTokenResponseDto refreshAccessToken(String refreshToken) {
        if (refreshToken == null || !jwtTokenProvider.validateRefreshToken(refreshToken)) {
            return null;
        }

        Claims claims = jwtTokenProvider.parseClaims(refreshToken);
        String encodedUserId = claims.getSubject();
        Long internalUserId = hashIdsUtils.decode(encodedUserId);

        Users user = usersRepository.findById(Long.valueOf(internalUserId)).orElse(null);
        if (user == null || user.getStatus() == UserStatus.WITHDRAWN) {
            return null;
        }

        String accessToken = jwtTokenProvider.createAccessToken(encodedUserId, user.getRole().name());

        return RefreshTokenResponseDto.builder()
                .accessToken(accessToken)
                .build();
    }

    public AuthStatusResponseDto getCurrentUserStatus() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getPrincipal() == null
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return AuthStatusResponseDto.builder().authenticated(false).build();
        }

        String userId = authentication.getPrincipal().toString();
        Users user;
        try {
            user = usersRepository.findById(Long.valueOf(userId)).orElse(null);
        } catch (NumberFormatException e) {
            return AuthStatusResponseDto.builder().authenticated(false).build();
        }

        if (user == null) {
            return AuthStatusResponseDto.builder().authenticated(false).build();
        }

        if (user.getStatus() == UserStatus.WITHDRAWN) {
            return AuthStatusResponseDto.builder().authenticated(false).build();
        }

        Certification latestCertification = certificationRepository
                .findTopByUser_IdOrderByCreatedAtDesc(user.getId())
                .orElse(null);

        return AuthStatusResponseDto.builder()
                .authenticated(true)
                .user(AuthStatusResponseDto.UserInfo.builder()
                        .id(hashIdsUtils.encode(user.getId()))
                        .nickname(user.getNickname())
                        .role(user.getRole().name())
                        .status(user.getStatus().name())
                        .certificationStatus(
                                latestCertification != null
                                        ? latestCertification.getCertificationStatus().name()
                                        : null
                        )
                        .surveyCompleted(isSurveyCompleted(userId))
                        .build())
                .build();
    }

    private UserRegistration findOrCreateUser(KakaoUserInfoResponseDto userInfo) {
        String oauthId = userInfo.getId().toString();

        Optional<Users> existingUser = usersRepository.findByOauthId(oauthId);
        if (existingUser.isPresent()) {
            return new UserRegistration(existingUser.get(), false);
        }

        KakaoUserInfoResponseDto.KakaoAccount.Profile profile = userInfo.getKakaoAccount().getProfile();
        Users newUser = Users.builder()
                .oauthId(oauthId)
                .email(userInfo.getKakaoAccount().getEmail())
                .nickname(profile.getNickname())
                .profileImageUrl(profile.getProfileImageUrl())
                .build();

        return new UserRegistration(usersRepository.save(newUser), true);
    }

    private KakaoTokenResponseDto getKakaoToken(String code) {
        String tokenUri = "https://kauth.kakao.com/oauth/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", kakaoProperties.getClientId());
        params.add("client_secret", kakaoProperties.getClientSecret());
        params.add("redirect_uri", kakaoProperties.getRedirectUri());
        params.add("code", code);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            KakaoTokenResponseDto response = restTemplate.postForObject(tokenUri, request, KakaoTokenResponseDto.class);
            if (response == null || response.getAccessToken() == null) {
                throw new BusinessException(ErrorCode.KAKAO_API_ERROR);
            }
            return response;
        } catch (HttpClientErrorException.BadRequest e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.KAKAO_API_ERROR);
        }
    }

    private KakaoUserInfoResponseDto getKakaoUserInfo(String accessToken) {
        String userInfoUri = "https://kapi.kakao.com/v2/user/me";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<?> request = new HttpEntity<>(headers);

        try {
            KakaoUserInfoResponseDto response = restTemplate.exchange(
                    userInfoUri,
                    HttpMethod.GET,
                    request,
                    KakaoUserInfoResponseDto.class
            ).getBody();

            if (response == null || response.getId() == null || response.getKakaoAccount() == null
                    || response.getKakaoAccount().getProfile() == null) {
                throw new BusinessException(ErrorCode.KAKAO_API_ERROR);
            }
            return response;
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.KAKAO_API_ERROR);
        }
    }

    private boolean isSurveyCompleted(String userId) {
        return userPreferencesRepository.findByUserId(Long.valueOf(userId))
                .map(userPreferences -> Boolean.TRUE.equals(userPreferences.getIsCompleted()))
                .orElse(false);
    }

    public record LoginResult(LoginResponseDto response, String refreshToken) {
    }

    private record UserRegistration(Users user, boolean isNewUser) {
    }
}
