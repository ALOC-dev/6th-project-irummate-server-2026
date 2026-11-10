package com.irummate.domain.auth.repository;

import com.irummate.domain.auth.entity.LoginSession;
import com.irummate.domain.user.entity.UserRole;
import com.irummate.domain.user.entity.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface LoginSessionRepository extends JpaRepository<LoginSession, UUID> {

    // Scalar projection avoids materializing Users and loading its inverse one-to-one associations.
    @Query("""
            select account.id as userId, account.role as role, account.status as status,
                   session.expiresAt as expiresAt, session.revokedAt as revokedAt
              from LoginSession session join session.user account
             where session.id = :id
            """)
    Optional<SessionAuthentication> findAuthenticationById(@Param("id") UUID id);

    interface SessionAuthentication {
        Long getUserId();
        UserRole getRole();
        UserStatus getStatus();
        LocalDateTime getExpiresAt();
        LocalDateTime getRevokedAt();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LoginSession> findByRefreshTokenHash(String refreshTokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update LoginSession session
               set session.revokedAt = :revokedAt
             where session.user.id = :userId
               and session.revokedAt is null
            """)
    int revokeAllByUserId(@Param("userId") Long userId, @Param("revokedAt") LocalDateTime revokedAt);
}
