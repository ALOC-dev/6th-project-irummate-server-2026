package com.irummate.domain.auth.controller;

import com.irummate.domain.auth.dto.AuthStatusResponseDto;
import com.irummate.domain.auth.dto.RefreshTokenResponseDto;
import com.irummate.domain.auth.service.AuthService;
import com.irummate.global.config.KakaoProperties;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.response.GlobalApiResponse;
import com.irummate.global.jwt.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private static final Set<String> TRUSTED_ORIGINS = Set.of(
            "https://irummate.com",
            "https://www.irummate.com",
            "http://localhost.com:5173"
    );

    private final AuthService authService;
    private final KakaoProperties kakaoProperties;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    @GetMapping("/kakao/start")
    public ResponseEntity<Void> kakaoStart(HttpServletResponse response) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        ResponseCookie stateCookie = ResponseCookie.from("oauthState", state)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api/auth/kakao")
                .maxAge(Duration.ofMinutes(5))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, stateCookie.toString());

        String authorizationUrl = UriComponentsBuilder
                .fromUriString("https://kauth.kakao.com/oauth/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", kakaoProperties.getClientId())
                .queryParam("redirect_uri", kakaoProperties.getRedirectUri())
                .queryParam("state", state)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUriString();

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, authorizationUrl)
                .build();
    }

    /**
     * [GET] /api/auth/kakao/callback
     * 역할: 카카오가 로그인을 마친 사용자를 이 주소로 보내면서 일회용 티켓('code')을 던져줍니다.
     * 그 티켓을 받아 실제 회원가입/로그인 처리를 완료하고 토큰을 발급하는 핵심 API입니다.
     */
    @GetMapping("/kakao/callback")
    public ResponseEntity<GlobalApiResponse<?>> kakaoCallback(
            @RequestParam(value = "code", required = false) String code, // 카카오가 보내준 인가 코드
            @RequestParam(value = "state", required = false) String state,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        // 만약 카카오가 코드를 안 보냈거나 비어있다면 잘못된 요청시 에러를 반환합니다.
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,"인가 코드가 필요합니다.");
        }
        String expectedState = extractCookie(servletRequest, "oauthState");
        if (state == null || expectedState == null || !expectedState.equals(state)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "유효하지 않은 로그인 요청입니다.");
        }

        // 1. [비즈니스 로직 호출]: 서비스에게 일회용 코드를 주면서 카카오와 통신하여 회원가입/로그인을 시키고
        //    그 결과물(Access/Refresh Token 및 유저정보)을 받아옵니다.
        AuthService.LoginResult loginResult = authService.loginOrRegister(code);

        // 2. [보안 조치]: 탈취 위험이 높은 Refresh Token은 브라우저의 로컬 스토리지에 저장하면 보안에 취약하므로,
        //    자바스크립트가 접근할 수 없는 안전한 'HttpOnly 쿠키' 형태로 만듭니다.
        ResponseCookie refreshTokenCookie = createRefreshCookie(loginResult.refreshToken());

        // 3. [헤더 설정]: 만든 HttpOnly 쿠키를 브라우저의 지갑에 쏙 넣어주도록 응답 헤더(Set-Cookie)에 추가합니다.
        servletResponse.addHeader("Set-Cookie", refreshTokenCookie.toString());
        servletResponse.addHeader("Set-Cookie", clearCookie("oauthState", "/api/auth/kakao").toString());

        // 4. [최종 반환]: 프론트엔드에게 Access Token과 유저 간략 정보가 담긴 데이터를 규격 상자에 담아 반환합니다.
        return ResponseEntity.ok(GlobalApiResponse.success(HttpStatus.OK, "Access Token 반환 성공", loginResult.response()));
    }

    /**
     * [POST] /api/auth/refresh
     * 역할: 로그인의 유효기간(Access Token 만료)이 다 되었을 때, 브라우저 쿠키에 숨겨둔
     * Refresh Token을 꺼내어 검증한 뒤 새로운 Access Token을 갱신(재발급)해 줍니다.
     */
    @PostMapping("/refresh")
    public ResponseEntity<GlobalApiResponse<RefreshTokenResponseDto>> refresh(
            HttpServletRequest request,
            HttpServletResponse servletResponse
    ) {
        validateOrigin(request);
        String refreshToken = extractRefreshToken(request);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Refresh Token이 필요합니다.");
        }

        AuthService.RefreshResult result = authService.refreshAccessToken(refreshToken);
        servletResponse.addHeader("Set-Cookie", createRefreshCookie(result.refreshToken()).toString());

        return ResponseEntity.ok(GlobalApiResponse.success(HttpStatus.OK, "Access Token 재발급 성공", result.response()));
    }

    @PostMapping("/logout")
    public ResponseEntity<GlobalApiResponse<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        validateOrigin(request);
        authService.logout(extractRefreshToken(request));
        // 똑같은 이름의 쿠키를 만들되, 만료시간(maxAge)을 0으로 주어 브라우저가 즉시 삭제하게 만듭니다.
        ResponseCookie clearCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0) // 즉시 만료
                .sameSite("Lax")
                .build();

        response.addHeader("Set-Cookie", clearCookie.toString());
        return ResponseEntity.ok(GlobalApiResponse.success(HttpStatus.OK,"로그아웃되었습니다.", null));
    }

    @GetMapping("/status")
    public ResponseEntity<GlobalApiResponse<AuthStatusResponseDto>> status() {
        AuthStatusResponseDto response = authService.getCurrentUserStatus();
        return ResponseEntity.ok(GlobalApiResponse.success(HttpStatus.OK,"유저 정보 반환 성공", response));
    }

    //사용자의 HTTP 요청 속 refresh토큰만 추출
    private String extractRefreshToken(HttpServletRequest request) {
        return extractCookie(request, "refreshToken");
    }

    private String extractCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue(); // 찾았다면 토큰 문자열 반환
            }
        }

        return null;
    }

    private ResponseCookie createRefreshCookie(String token) {
        return ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(Duration.ofMillis(jwtProperties.getRefreshTokenExpiration()))
                .sameSite("Lax")
                .build();
    }

    private ResponseCookie clearCookie(String name, String path) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(true)
                .path(path)
                .maxAge(Duration.ZERO)
                .sameSite("Lax")
                .build();
    }

    private void validateOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !TRUSTED_ORIGINS.contains(origin)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "허용되지 않은 요청 출처입니다.");
        }
    }
}
