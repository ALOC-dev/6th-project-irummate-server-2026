package com.irummate.domain.privacy.repository;

import com.irummate.domain.privacy.entity.PrivacyConsents;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivacyConsentsRepository extends JpaRepository<PrivacyConsents, Long> {
}
