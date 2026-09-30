package com.irummate.domain.auth.service;

import com.irummate.domain.auth.dto.AuthStatusResponseDto;
import com.irummate.domain.auth.dto.KakaoTokenResponseDto;
import com.irummate.domain.auth.dto.KakaoUserInfoResponseDto;
import com.irummate.domain.auth.dto.LoginResponseDto;
import com.irummate.domain.auth.dto.RefreshTokenResponseDto;
import com.irummate.domain.auth.entity.LoginSession;
import com.irummate.domain.auth.repository.LoginSessionRepository;
import com.irummate.domain.certification.entity.Certification;
import com.irummate.domain.certification.repository.CertificationRepository;
import com.irummate.domain.survey.repository.UserPreferencesRepository;
import com.irummate.domain.user.entity.UserStatus;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.domain.user.repository.UserDetailsRepository;
import com.irummate.global.config.KakaoProperties;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.jwt.JwtTokenProvider;
import com.irummate.global.util.HashIdsUtils;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
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
    private final LoginSessionRepository loginSessionRepository;
    private final UserDetailsRepository userDetailsRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    private final RestTemplate restTemplate = new RestTemplate();

    @Transactional
    public LoginResult loginOrRegister(String code) {
        KakaoTokenResponseDto tokenResponse = getKakaoToken(code);
        KakaoUserInfoResponseDto userInfo = getKakaoUserInfo(tokenResponse.getAccessToken());

        UserRegistration userRegistration = findOrCreateUser(userInfo);
        Users user = userRegistration.user();
        validateAccountUsable(user);
        Long internalUserId = user.getId();
        String encodedUserId = hashIdsUtils.encode(internalUserId);

        String refreshToken = generateRefreshToken();
        LoginSession loginSession = loginSessionRepository.save(new LoginSession(
                user,
                hashRefreshToken(refreshToken),
                refreshExpiresAt()
        ));
        String accessToken = jwtTokenProvider.createAccessToken(
                encodedUserId,
                user.getRole().name(),
                loginSession.getId().toString()
        );

        LoginResponseDto response = LoginResponseDto.builder()
                .accessToken(accessToken)
                .isNewUser(userRegistration.isNewUser())
                .tokenType("Bearer")
                .accessTokenExpiresIn(jwtTokenProvider.getAccessTokenExpiration() / 1000)
                .user(LoginResponseDto.UserInfo.builder()
                        .id(encodedUserId)
                        .nickname(user.getNickname())
                        .role(user.getRole().name())
                        .status(user.getStatus().name())
                        .build())
                .build();

        return new LoginResult(response, refreshToken);
    }

    @Transactional
    public RefreshResult refreshAccessToken(String refreshToken) {
        LoginSession session = loginSessionRepository.findByRefreshTokenHash(hashRefreshToken(refreshToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        if (!session.isAvailable(now)) {
            session.revoke();
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Users user = session.getUser();
        validateAccountUsable(user);

        String newRefreshToken = generateRefreshToken();
        session.rotate(hashRefreshToken(newRefreshToken), refreshExpiresAt());

        String accessToken = jwtTokenProvider.createAccessToken(
                hashIdsUtils.encode(user.getId()),
                user.getRole().name(),
                session.getId().toString()
        );

        RefreshTokenResponseDto response = RefreshTokenResponseDto.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .accessTokenExpiresIn(jwtTokenProvider.getAccessTokenExpiration() / 1000)
                .build();

        return new RefreshResult(response, newRefreshToken);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        loginSessionRepository.findByRefreshTokenHash(hashRefreshToken(refreshToken))
                .ifPresent(LoginSession::revoke);
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
                                currentCertificationStatus(latestCertification)
                        )
                        .detailsCompleted(userDetailsRepository.existsById(user.getId()))
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

    private void validateAccountUsable(Users user) {
        if (user.getStatus() == UserStatus.BANNED) {
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED);
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN);
        }
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashRefreshToken(String refreshToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
    }

    private LocalDateTime refreshExpiresAt() {
        return LocalDateTime.now(ZoneId.of("Asia/Seoul"))
                .plusNanos(jwtTokenProvider.getRefreshTokenExpiration() * 1_000_000);
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

    private String currentCertificationStatus(Certification certification) {
        if (certification == null) {
            return "NONE";
        }
        if (certification.getCertificationStatus() == com.irummate.domain.certification.entity.CertificationStatus.APPROVED
                && certification.getExpiresAt() != null
                && !certification.getExpiresAt().isAfter(LocalDateTime.now(ZoneId.of("Asia/Seoul")))) {
            return com.irummate.domain.certification.entity.CertificationStatus.EXPIRED.name();
        }
        return certification.getCertificationStatus().name();
    }

    public record LoginResult(LoginResponseDto response, String refreshToken) {
    }

    public record RefreshResult(RefreshTokenResponseDto response, String refreshToken) {
    }

    private record UserRegistration(Users user, boolean isNewUser) {
    }
}
