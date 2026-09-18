package com.irummate.domain.survey.controller;

import com.irummate.domain.survey.dto.UserPreferencesRequestDto;
import com.irummate.domain.survey.dto.UserPreferencesResponseDto;
import com.irummate.domain.survey.service.SurveyService;
import com.irummate.global.aop.RequiresSurvey;
import com.irummate.global.response.GlobalApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/surveys")
public class SurveyController {

    private final SurveyService surveyService;

    @Autowired
    public SurveyController(SurveyService surveyService){
        this.surveyService = surveyService;
    }


    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "설문 제출 성공"),
            @ApiResponse(responseCode = "400", description = "필수 항목 누락 / 값 범위 초과"),
            @ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
            @ApiResponse(responseCode = "404", description = "유저를 찾을 수 없습니다."),
            @ApiResponse(responseCode = "409", description = "이미 설문을 제출한 상태입니다.")
    })
    @PostMapping
    public ResponseEntity<GlobalApiResponse<?>> saveUserPreferences(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserPreferencesRequestDto requestDto){

        surveyService.saveSurveyStatus(userId, requestDto);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GlobalApiResponse.success(
                        HttpStatus.CREATED,
                        "설문이 제출되었습니다. 매칭 대기 상태로 전환됩니다.",
                        null
                ));
    }


    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "설문 내용 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
            @ApiResponse(responseCode = "403", description = "설문 작성이 필요합니다."),
            @ApiResponse(responseCode = "404", description = "설문 내역을 찾을 수 없습니다.")
    })
    @GetMapping("/me")
    @RequiresSurvey
    public ResponseEntity<GlobalApiResponse<?>> getUserPreferences(
            @AuthenticationPrincipal Long userId
    ){
        UserPreferencesResponseDto responseDto = surveyService.getSurveyStatus(userId);

        return ResponseEntity.ok(
                GlobalApiResponse.success(HttpStatus.OK, "설문 내용 조회 성공", responseDto)
        );
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "설문 수정 성공"),
            @ApiResponse(responseCode = "400", description = "필수 항목 누락 / 값 범위 초과"),
            @ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
            @ApiResponse(responseCode = "403", description = "매칭 시작일 이후에는 설문을 수정할 수 없습니다."),
            @ApiResponse(responseCode = "404", description = "설문 내역 또는 매칭 날짜를 찾을 수 없습니다."),
            @ApiResponse(responseCode = "409", description = "이미 매칭이 확정된 상태입니다.")
    })
    @PatchMapping("/me")
    @RequiresSurvey
    public ResponseEntity<GlobalApiResponse<?>> updateUserPreferences(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserPreferencesRequestDto requestDto
    ){
        UserPreferencesResponseDto responseDto = surveyService.updateSurveyStatus(userId, requestDto);

        return ResponseEntity.ok(
                GlobalApiResponse.success(HttpStatus.OK, "설문 수정 성공", responseDto)
        );
    }
}
