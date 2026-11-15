package com.irummate.domain.recruitment.repository;

import com.irummate.domain.recruitment.entity.RecruitmentParticipantRole;
import com.irummate.domain.recruitment.entity.RecruitmentParticipantStatus;
import com.irummate.domain.recruitment.entity.RecruitmentParticipants;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecruitmentParticipantsRepository extends JpaRepository<RecruitmentParticipants, Long> {


    boolean existsByRecruitment_IdAndUser_Id(
            Long recruitmentId,
            Long userId
    );


    Optional<RecruitmentParticipants> findByRecruitment_IdAndUser_Id(
            Long recruitmentId,
            Long userId
    );


    // 참여 이력의 모집글과 작성자를 함께 조회해 추가 조회 반복 방지
    @EntityGraph(attributePaths = {"recruitment", "recruitment.author"})
    Page<RecruitmentParticipants> findAllByUser_IdOrderByJoinedAtDescIdDesc(
            Long userId,
            Pageable pageable
    );






    // 참여자 목록. Users 엔티티를 읽지 않고 필요한 컬럼만 조회해 쿼리 1번으로 처리
    // 작성자의 참여 기록이 가장 먼저 생기므로 참여 시각 순으로 정렬하면 작성자가 맨 앞
    @Query("""
    select u.id as userId,
           u.nickname as nickname,
           u.profileImageUrl as profileImageUrl,
           p.role as role,
           p.joinedAt as joinedAt
    from RecruitmentParticipants p
    join p.user u
    where p.recruitment.id = :recruitmentId
      and p.status = :status
    order by p.joinedAt asc, p.id asc
    """)
    List<ParticipantRow> findParticipantRows(
            @Param("recruitmentId") Long recruitmentId,
            @Param("status") RecruitmentParticipantStatus status
    );

    interface ParticipantRow {
        Long getUserId();
        String getNickname();
        String getProfileImageUrl();
        RecruitmentParticipantRole getRole();
        LocalDateTime getJoinedAt();
    }




}
