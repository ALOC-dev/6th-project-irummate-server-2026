package com.irummate.domain.privacy.repository;

import com.irummate.domain.privacy.entity.ConsentHistory;
import com.irummate.domain.privacy.entity.ConsentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsentHistoryRepository extends JpaRepository<ConsentHistory, Long> {
    Optional<ConsentHistory> findFirstByUserIdAndConsentTypeOrderByIdAsc(Long userId, ConsentType consentType);
}
