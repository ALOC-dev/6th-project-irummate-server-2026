package com.irummate.global.aop;

import com.irummate.domain.block.service.UserBlockService;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

// 기존 인증·인가 검증(100)이 완료된 뒤 차단을 검사한다.
@Aspect
@Component
@Order(200)
@RequiredArgsConstructor
public class UserBlockAspect {
    private final UserBlockService userBlockService;
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();

    @Before("@annotation(checkUserBlock)")
    public void checkUserBlock(JoinPoint joinPoint, CheckUserBlock checkUserBlock) {
        Long currentUserId = ValidationAspect.extractUserId();
        Long targetUserId = resolveTargetId(joinPoint, checkUserBlock.targetId());
        userBlockService.validateNotBlocked(currentUserId, targetUserId);
    }

    // 파라미터 이름과 #p0/#a0 위치 참조를 지원한다. DTO 프로퍼티는 읽기만 허용한다.
    private Long resolveTargetId(JoinPoint joinPoint, String expression) {
        var signature = (MethodSignature) joinPoint.getSignature();
        var method = AopUtils.getMostSpecificMethod(signature.getMethod(), joinPoint.getTarget().getClass());
        var context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
        Object[] args = joinPoint.getArgs();
        String[] names = parameterNames.getParameterNames(method);
        context.setVariable("args", args);
        for (int i = 0; i < args.length; i++) {
            context.setVariable("p" + i, args[i]);
            context.setVariable("a" + i, args[i]);
            if (names != null) {
                context.setVariable(names[i], args[i]);
            }
        }

        Object targetId;
        try {
            targetId = parser.parseExpression(expression).getValue(context);
        } catch (ParseException | EvaluationException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "차단 검사 대상 식별자를 확인할 수 없습니다.");
        }
        // 문자열을 숫자로 자동 변환하지 않는다. Hashids 해석은 호출 경계에서 먼저 수행한다.
        if (!(targetId instanceof Long id) || id <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "차단 검사 대상은 양수인 Long 회원 ID여야 합니다.");
        }
        return id;
    }
}
