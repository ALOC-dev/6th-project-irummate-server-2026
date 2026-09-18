package com.irummate.domain.chat.repository;

import com.irummate.domain.chat.dto.ChatRoomPartnerDto;
import com.irummate.domain.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    long countByStatus(com.irummate.domain.chat.entity.ChatRoomStatus status);

    @Query("""
            SELECT COUNT(cr)
            FROM ChatRoom cr
            JOIN MatchRequests mr ON mr.id = cr.matchRequestId
            WHERE mr.userLow.role = com.irummate.domain.user.entity.UserRole.USER
              AND mr.userLow.status = com.irummate.domain.user.entity.UserStatus.ACTIVE
              AND mr.userHigh.role = com.irummate.domain.user.entity.UserRole.USER
              AND mr.userHigh.status = com.irummate.domain.user.entity.UserStatus.ACTIVE
            """)
    long countActiveUserRooms();

    @Query("""
            SELECT COUNT(cr)
            FROM ChatRoom cr
            JOIN MatchRequests mr ON mr.id = cr.matchRequestId
            WHERE cr.status = :status
              AND mr.userLow.role = com.irummate.domain.user.entity.UserRole.USER
              AND mr.userLow.status = com.irummate.domain.user.entity.UserStatus.ACTIVE
              AND mr.userHigh.role = com.irummate.domain.user.entity.UserRole.USER
              AND mr.userHigh.status = com.irummate.domain.user.entity.UserStatus.ACTIVE
            """)
    long countActiveUserRoomsByStatus(@Param("status") com.irummate.domain.chat.entity.ChatRoomStatus status);

    @Query("""
            SELECT COUNT(cr)
            FROM ChatRoom cr
            JOIN MatchRequests mr ON mr.id = cr.matchRequestId
            WHERE cr.status = :status
              AND (mr.userLow.id = :userId OR mr.userHigh.id = :userId)
            """)
    long countByParticipantIdAndStatus(
            @Param("userId") Long userId,
            @Param("status") com.irummate.domain.chat.entity.ChatRoomStatus status
    );

    // 매칭 요청 하나에 채팅방이 하나만 생성되도록 확인할 때 사용한다.
    Optional<ChatRoom> findByMatchRequestId(Long matchRequestId);

    // 채팅방 목록에 필요한 상대방 정보를 매칭 요청과 유저 정보에서 한 번에 조회한다.
    @Query("""
            SELECT new com.irummate.domain.chat.dto.ChatRoomPartnerDto(
                cr.id,
                CASE
                    WHEN mr.userLow.id = :userId THEN mr.userHigh.id
                    ELSE mr.userLow.id
                END,
                CASE
                    WHEN mr.userLow.id = :userId THEN mr.userHigh.nickname
                    ELSE mr.userLow.nickname
                END,
                CASE
                    WHEN mr.userLow.id = :userId THEN mr.userHigh.profileImageUrl
                    ELSE mr.userLow.profileImageUrl
                END,
                cr.status,
                CASE
                    WHEN mr.userLow.id = :userId THEN mr.userLowStatus
                    ELSE mr.userHighStatus
                END,
                CASE
                    WHEN mr.userLow.id = :userId THEN mr.userHighStatus
                    ELSE mr.userLowStatus
                END
            )
            FROM ChatRoom cr
            JOIN MatchRequests mr ON mr.id = cr.matchRequestId
            WHERE mr.userLow.id = :userId
               OR mr.userHigh.id = :userId
            ORDER BY cr.createdAt DESC
            """)
    List<ChatRoomPartnerDto> findRoomPartnersByUserId(@Param("userId") Long userId);

    // 현재 유저가 참여한 채팅방 엔티티 목록을 조회한다.
    @Query("""
            SELECT cr
            FROM ChatRoom cr
            JOIN MatchRequests mr ON mr.id = cr.matchRequestId
            WHERE mr.userLow.id = :userId
               OR mr.userHigh.id = :userId
            ORDER BY cr.createdAt DESC
            """)
    List<ChatRoom> findAllByParticipantId(@Param("userId") Long userId);

    // 요청한 유저가 해당 채팅방과 연결된 매칭의 당사자인지 확인한다.
    @Query("""
            SELECT COUNT(cr) > 0
            FROM ChatRoom cr
            JOIN MatchRequests mr ON mr.id = cr.matchRequestId
            WHERE cr.id = :roomId
              AND (mr.userLow.id = :userId OR mr.userHigh.id = :userId)
            """)
    boolean existsByRoomIdAndParticipantId(@Param("roomId") Long roomId, @Param("userId") Long userId);

    // 현재 유저의 상대방 userId를 조회한다.
    @Query("""
            SELECT CASE
                WHEN mr.userLow.id = :userId THEN mr.userHigh.id
                ELSE mr.userLow.id
            END
            FROM ChatRoom cr
            JOIN MatchRequests mr ON mr.id = cr.matchRequestId
            WHERE cr.id = :roomId
              AND (mr.userLow.id = :userId OR mr.userHigh.id = :userId)
            """)
    Optional<Long> findPartnerIdByRoomIdAndUserId(@Param("roomId") Long roomId, @Param("userId") Long userId);
}
