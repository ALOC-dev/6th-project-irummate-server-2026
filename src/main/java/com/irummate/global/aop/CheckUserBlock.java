package com.irummate.global.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 인증 사용자와 대상 사이의 양방향 차단을 검사한다.
 * targetId에는 Long 회원 PK를 반환하는 SpEL을 지정한다.
 * 예: @CheckUserBlock(targetId = "#targetUserId") 또는 "#p0".
 * Spring Bean의 외부 호출에 적용하며 Hashids 문자열·목록·WebSocket 검증에는 사용하지 않는다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckUserBlock {
    String targetId();
}
