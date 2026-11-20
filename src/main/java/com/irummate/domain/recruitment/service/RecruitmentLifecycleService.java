package com.irummate.domain.recruitment.service;

import com.irummate.domain.recruitment.repository.RecruitmentsRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Clock;

@Service
@RequiredArgsConstructor
public class RecruitmentLifecycleService {

    private static final int BATCH_SIZE = 50;

    private final RecruitmentsRepository recruitmentsRepository;
    private final Clock clock;

    // 영속성 관리를 연결(해당 service가 관리하는 영속성)
    @PersistenceContext
    private EntityManager entityManager;


    @Transactional
    public int endRecruitments() {
        applyLockTimeout();
        LocalDateTime now = LocalDateTime.now(clock);

        // 엔티티 조회 없이 최대 50건 종료. 대상 선택과 갱신은 같은 SQL에서 수행
        return recruitmentsRepository.endRecruitmentsInBatch(now, BATCH_SIZE);
    }

    @Transactional
    public int closeRecruitmentsByDeadline() {
        applyLockTimeout();
        LocalDateTime now = LocalDateTime.now(clock);

        return recruitmentsRepository.closeRecruitmentsByDeadlineInBatch(now, BATCH_SIZE);
    }

    private void applyLockTimeout() {
        // 행 잠금은 건너뛰지만 테이블 잠금 등의 대기는 이번 트랜잭션에서 1초로 제한
        entityManager.createNativeQuery("SET LOCAL lock_timeout = '1s'")
                .executeUpdate();
    }


}
