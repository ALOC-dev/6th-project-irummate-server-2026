package com.irummate.domain.admin.repository;

import com.irummate.domain.admin.entity.UserBan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserBanRepository extends JpaRepository<UserBan, Long> {
    Optional<UserBan> findTopByUser_IdAndLiftedAtIsNullOrderByBannedAtDesc(Long userId);
}
