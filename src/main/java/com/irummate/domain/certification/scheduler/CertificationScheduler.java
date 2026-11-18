package com.irummate.domain.certification.scheduler;


import com.irummate.domain.certification.service.CertificationLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CertificationScheduler {

    private final CertificationLifecycleService certificationLifecycleService;


    @Scheduled(cron = "0 0 0 1 1,7 *", zone = "Asia/Seoul")
    public void expirePreviousSemesters(){

        try {
            int updatedCount =
                    certificationLifecycleService.expirePreviousSemesters();

            if (updatedCount > 0) {
                log.info("지난 학기 인증 만료 완료: count={}", updatedCount);
            }
        } catch (RuntimeException e) {
            log.error("지난 학기 인증 만료 실패", e);
        }


    }
}
