package com.irummate.domain.matching.repository;

import com.irummate.domain.matching.entity.MatchingConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchingConfigRepository extends JpaRepository<MatchingConfig,Long> {
}
