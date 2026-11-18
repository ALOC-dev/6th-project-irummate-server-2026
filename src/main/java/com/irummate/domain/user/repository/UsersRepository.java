package com.irummate.domain.user.repository;

import com.irummate.domain.user.entity.UserRole;
import com.irummate.domain.user.entity.UserStatus;
import com.irummate.domain.user.entity.Users;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsersRepository extends JpaRepository<Users, Long> {
    public Optional<Users> findByOauthId(String oauthId);
    public Optional<Users> findById(Long userId);

    // 인증 기록이 아직 없어도 같은 사용자의 동시 생성을 직렬화
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Users u WHERE u.id = :userId")
    Optional<Users> findByIdForUpdate(@Param("userId") Long userId);


    // match에서 여러 user와 설문을 동시에 가져오기 위한 query
    @Query("""
    SELECT DISTINCT u
    FROM Users u
    JOIN FETCH u.userPreferences
    LEFT JOIN FETCH u.userDetails
    WHERE u.id IN :userIds
    """)
    List<Users> findAllByIdsWithPreferences(
            @Param("userIds") List<Long> userIds
    );



    @Query("""
            SELECT u
            FROM Users u
            LEFT JOIN FETCH u.userDetails
            LEFT JOIN FETCH u.userPreferences
            WHERE u.role = :role
              AND u.status = :status
            ORDER BY u.createdAt DESC
            """)
    List<Users> findAllByRoleAndStatusWithDetails(@Param("role") UserRole role, @Param("status") UserStatus status);
}
