package com.irummate.domain.user.repository;

import com.irummate.domain.user.entity.UserRole;
import com.irummate.domain.user.entity.UserStatus;
import com.irummate.domain.user.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsersRepository extends JpaRepository<Users, Long> {
    public Optional<Users> findByOauthId(String oauthId);
    public Optional<Users> findById(Long userId);

    @Query("""
            SELECT u
            FROM Users u
            LEFT JOIN FETCH u.userDetails
            WHERE u.role = :role
              AND u.status = :status
            ORDER BY u.createdAt DESC
            """)
    List<Users> findAllByRoleAndStatusWithDetails(@Param("role") UserRole role, @Param("status") UserStatus status);
}
