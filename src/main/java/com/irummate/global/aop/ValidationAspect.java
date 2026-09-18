package com.irummate.global.aop;

import com.irummate.domain.matching.entity.MatchingConfig;
import com.irummate.domain.matching.repository.MatchingConfigRepository;
import com.irummate.domain.survey.entity.UserPreferences;
import com.irummate.domain.survey.repository.UserPreferencesRepository;
import com.irummate.domain.user.entity.UserStatus;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;

@Slf4j
@Aspect
@Component
public class ValidationAspect {

    private final UserPreferencesRepository userPreferencesRepository;
    private final UsersRepository usersRepository;
    private final MatchingConfigRepository matchingConfigRepository;

    @Autowired
    public ValidationAspect(UserPreferencesRepository userPreferencesRepository,
                            UsersRepository usersRepository,
                            MatchingConfigRepository matchingConfigRepository){
        this.userPreferencesRepository = userPreferencesRepository;
        this.usersRepository = usersRepository;
        this.matchingConfigRepository = matchingConfigRepository;
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

        Users user = usersRepository.findById(userId)
                .orElseThrow(()->new BusinessException(ErrorCode.USER_NOT_FOUND));

        if(user.getStatus() != UserStatus.ACTIVE){
            throw new BusinessException(ErrorCode.FORBIDDEN);
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

        MatchingConfig matchingConfig = matchingConfigRepository.findById(MatchingConfig.SINGLETON_ID)
                .orElseThrow(()->new BusinessException(ErrorCode.MATCH_DATE_NOT_FOUND));

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        if(today.isBefore(matchingConfig.getMatchStartDate()) || today.isAfter(matchingConfig.getMatchEndDate())){
            throw new BusinessException(ErrorCode.MATCH_NOT_OPEN);
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
