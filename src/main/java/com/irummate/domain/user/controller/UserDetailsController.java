package com.irummate.domain.user.controller;

import com.irummate.domain.user.dto.UserDetailsRequestDto;
import com.irummate.domain.user.dto.UserDetailsResponseDto;
import com.irummate.domain.user.dto.UserDetailsUpdateRequestDto;
import com.irummate.domain.user.dto.UserProfileResponseDto;
import com.irummate.domain.user.dto.UserProfileUpdateRequestDto;
import com.irummate.domain.user.dto.UserProfileUpdateResponseDto;
import com.irummate.domain.user.service.UserDetailsService;
import com.irummate.global.response.GlobalApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserDetailsController {

    private final UserDetailsService userDetailsService;

    /**

     * @AuthenticationPrincipal:시큐리티 필터(JwtAuthenticationFilter)가 토큰을 검증한 뒤
     * 안전해진 유저의 PK 값(userId)을 자동으로 꺼내서 매개변수에 주입합니다.
     */
    @GetMapping("/me")
    public ResponseEntity<GlobalApiResponse<UserProfileResponseDto>> getProfile(
            @AuthenticationPrincipal Long userId
    ) {
        // 1. 서비스에 유저 ID를 넘겨 프로필 데이터를 조회합니다.
        UserProfileResponseDto response = userDetailsService.getProfile(userId);

        // 2. 성공 응답 형태(ApiResponse.success)에 담아 HTTP 상태코드 200(OK)로 반환합니다.
        return ResponseEntity.ok(GlobalApiResponse.success(HttpStatus.OK,"응답 성공", response));
    }

    /**
     * [PATCH] /api/users/me
     * 역할: 현재 로그인한 유저의 프로필(닉네임, 프로필이미지)을 수정합니다.
     * @RequestBody: 클라이언트가 보낸 JSON 데이터를 자바 객체(Dto)로 변환해줍니다.
     */
    @PatchMapping("/me")
    public ResponseEntity<GlobalApiResponse<UserProfileUpdateResponseDto>> updateProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserProfileUpdateRequestDto request
    ) {
        // 1. 서비스에 ID와 수정할 내용(request)을 넘겨 데이터를 업데이트합니다.
        UserProfileUpdateResponseDto response = userDetailsService.updateProfile(userId, request);

        // 2. 수정 완료 메시지와 함께 결과 데이터를 반환합니다.
        return ResponseEntity.ok(GlobalApiResponse.success(HttpStatus.OK, "프로필이 수정되었습니다.", response));
    }

    /**
     * [POST] /api/users/details
     * 역할: 카카오 로그인 직후, 서비스 이용에 필요한 추가 수집 정보(본명, 학번, 나이, 성별, 학과)를 최초 등록합니다.
     */
    @PostMapping("/details")
    public ResponseEntity<GlobalApiResponse<UserDetailsResponseDto>> createDetails(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserDetailsRequestDto request
    ) {
        // 1. 서비스에 유저 ID와 추가 정보 입력 데이터를 넘겨 상세정보(UserDetails)를 생성합니다.
        UserDetailsResponseDto response = userDetailsService.createDetails(userId, request);

        // 2. 새로 생성된 데이터이므로 HTTP 상태코드 201(CREATED)로 응답합니다.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GlobalApiResponse.success(HttpStatus.CREATED, "추가 정보가 등록되었습니다.", response));
    }

    @PatchMapping("/details")
    public ResponseEntity<GlobalApiResponse<UserDetailsResponseDto>> updateDetails(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserDetailsUpdateRequestDto request
    ) {
        UserDetailsResponseDto response = userDetailsService.updateDetails(userId, request);

        return ResponseEntity.ok(GlobalApiResponse.success(HttpStatus.OK, "기본 정보 수정 성공", response));
    }

    @DeleteMapping("/me")
    public ResponseEntity<GlobalApiResponse<Void>> withdraw(
            @AuthenticationPrincipal Long userId,
            HttpServletResponse servletResponse
    ) {
        userDetailsService.withdraw(userId);

        ResponseCookie clearCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(Duration.ZERO)
                .sameSite("Lax")
                .build();

        servletResponse.addHeader("Set-Cookie", clearCookie.toString());
        return ResponseEntity.ok(GlobalApiResponse.success(HttpStatus.OK, "회원 탈퇴 성공", null));
    }
}
