package com.irummate.domain.recruitment.scheduler;

import com.irummate.domain.recruitment.service.RecruitmentLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecruitmentScheduler {

    private final RecruitmentLifecycleService recruitmentLifecycleService;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void endRecruitments(){
        try {
            int updatedCount = recruitmentLifecycleService.endRecruitments();
            log.debug("모집글 일괄 종료 완료: count={}", updatedCount);
        } catch (PessimisticLockingFailureException e) {
            // 잠금 충돌은 다음 실행에서 재시도하므로 스택트레이스 없이 기록
            log.warn("모집글 일괄 종료 잠금 충돌: exception={}", e.getClass().getSimpleName());
        } catch (RuntimeException e) {
            log.error("모집글 일괄 종료 실패", e);
        }
    }

    // 종료 작업과 별도 트랜잭션으로 처리해 한쪽 실패가 다른 작업을 막지 않도록 분리
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void closeRecruitmentsByDeadline() {
        try {
            int updatedCount = recruitmentLifecycleService.closeRecruitmentsByDeadline();
            log.debug("모집글 일괄 마감 완료: count={}", updatedCount);
        } catch (PessimisticLockingFailureException e) {
            log.warn("모집글 일괄 마감 잠금 충돌: exception={}", e.getClass().getSimpleName());
        } catch (RuntimeException e) {
            log.error("모집글 일괄 마감 실패", e);
        }
    }
}
