package com.irummate.global.aop;

import com.irummate.domain.certification.entity.CertificationStatus;
import com.irummate.domain.certification.repository.CertificationRepository;
import com.irummate.domain.matching.service.MatchingPeriodProvider;
import com.irummate.domain.user.repository.UserDetailsRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.util.SemesterUtils;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Aspect
@Component
@RequiredArgsConstructor
// 공통 이용 조건을 차단 검증(UserBlockAspect)보다 먼저 확인
@Order(100)
public class ValidationAspect {

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
