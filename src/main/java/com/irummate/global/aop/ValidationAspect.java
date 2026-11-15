package com.irummate.global.aop;

import com.irummate.domain.certification.entity.CertificationStatus;
import com.irummate.domain.certification.repository.CertificationRepository;
import com.irummate.domain.matching.service.MatchingPeriodProvider;
import com.irummate.domain.survey.entity.UserPreferences;
import com.irummate.domain.survey.repository.UserPreferencesRepository;
import com.irummate.domain.user.entity.UserStatus;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UserDetailsRepository;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.util.SemesterUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
// 인증·인가 등 기존 검증을 차단 검증(UserBlockAspect)보다 먼저 수행한다.
@Order(100)
public class ValidationAspect {

    private final UserPreferencesRepository userPreferencesRepository;
    private final UsersRepository usersRepository;
    private final CertificationRepository certificationRepository;
    private final UserDetailsRepository userDetailsRepository;
    private final MatchingPeriodProvider matchingPeriodProvider;
    private final Clock clock;

    @Before("@annotation(com.irummate.global.aop.RequiresDetails)")
    public void checkDetailsCompleted(){
        Long userId = extractUserId();

        if (!userDetailsRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_DETAILS_REQUIRED);
        }
    }

    // survey가 완료된 상태인지 검증
    // @RequiresSurvey를 메서드 앞에 붙여서 사용
    @Before("@annotation(com.irummate.global.aop.RequiresSurvey)")
    public void checkSurveyCompleted(){
        Long userId = extractUserId();

        boolean surveyCompleted = userPreferencesRepository
                .findByUserId(userId)
                .map(UserPreferences::getIsCompleted)
                .orElse(false);

        if (!surveyCompleted) {
            throw new BusinessException(ErrorCode.SURVEY_REQUIRED);
        }
    }


    // 인증서 인증
    // @RequiresCertification
    @Before("@annotation(com.irummate.global.aop.RequiresCertification)")
    public void checkCertification(){
        Long userId = extractUserId();

        boolean approved = certificationRepository
                .findByUser_IdAndSemester(userId, SemesterUtils.currentSemester())
                .filter(certification -> certification.getCertificationStatus() == CertificationStatus.APPROVED)
                .filter(certification -> certification.getExpiresAt() == null
                        || certification.getExpiresAt().isAfter(LocalDateTime.now(clock)))
                .isPresent();

        if (!approved) {
            throw new BusinessException(ErrorCode.CERTIFICATION_REQUIRED);
        }
    }


    // 관리자 인증
    // @RequiresAuth
    @Before("@annotation(requiresAuth)")
    public void checkAuth(JoinPoint joinPoint, RequiresAuth requiresAuth){

        Long userId = extractUserId();

        log.debug("[AuthAspect] userId: {}, method: {}",
                userId, joinPoint.getSignature().getName());

        Users user = usersRepository.findById(userId)
                .orElseThrow(()->new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.BANNED || user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        AuthRole[] authRole = requiresAuth.roles();
        if (authRole.length == 0) return;

        boolean hasRole = Arrays.stream(authRole)
                .anyMatch(role -> role.name().equals(user.getRole().name()));

        if (!hasRole) {
            log.warn("[AuthAspect] 권한 없음 - userId: {}, role: {}, required: {}",
                    userId, user.getRole(), Arrays.toString(authRole));
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }


    // 매칭 날짜 검증
    // @RequiresMatchDate
    @Before("@annotation(com.irummate.global.aop.RequiresMatchDate)")
    public void checkMatchDate(){
        if(!matchingPeriodProvider.getRequired().isMatchOpen(LocalDate.now(clock))){
            throw new BusinessException(ErrorCode.MATCH_NOT_OPEN);
        }

    }

    // 설문 날짜 검증
    // @RequriesSurveyDate
    @Before("@annotation(com.irummate.global.aop.RequiresSurveyDate)")
    public void checkSurveyDate(){
        if(!matchingPeriodProvider.getRequired().isSurveyOpen(LocalDate.now(clock))){
            throw new BusinessException(ErrorCode.SURVEY_NOT_OPEN);
        }
    }

    // 매개변수로 들어오는 userId 확인
    public static Long extractUserId(){

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if(authentication != null && authentication.getPrincipal() instanceof  Long userId){
            return userId;
        }

        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
