package com.irummate.domain.recruitment.repository;

import com.irummate.domain.recruitment.entity.RecruitmentParticipantStatus;
import com.irummate.domain.recruitment.entity.RecruitmentStatus;
import com.irummate.domain.recruitment.entity.RecruitmentVisibility;
import com.irummate.domain.recruitment.entity.Recruitments;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RecruitmentsRepository extends JpaRepository<Recruitments, Long> {

    // 게시글 전체를 가져오는 함수
    @Override
    @EntityGraph(attributePaths = "author")
    Page<Recruitments> findAll(Pageable pageable);


    // 게시글 상태에 따른 분류
    @EntityGraph(attributePaths = "author")
    Page<Recruitments> findAllByStatus(
            RecruitmentStatus status,
            Pageable pageable
    );


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT r
    FROM Recruitments r
    WHERE r.id = :recruitmentId
    """)
    Optional<Recruitments> findByIdForUpdate(
            @Param("recruitmentId") Long recruitmentId
    );



    @Query(
            value = """
        select r as recruitment,
               case
                   when a.id = :userId then 'MINE'
                   when p.id is not null then 'JOINED'
                   else 'NONE'
               end as participating
        from Recruitments r
        join fetch r.author a
        left join RecruitmentParticipants p
            on p.recruitment = r
            and p.user.id = :userId
            and p.status = :joinedStatus
        where r.visibility = :visibility
        order by r.createdAt desc, r.id desc
        """,
            countQuery = """
        select count(r)
        from Recruitments r
        where r.visibility = :visibility
        """
    )
    Page<RecruitmentListRow> findRecruitmentList(
            @Param("userId") Long userId,
            @Param("joinedStatus") RecruitmentParticipantStatus joinedStatus,
            @Param("visibility") RecruitmentVisibility visibility,
            Pageable pageable
    );


    public interface RecruitmentListRow {
        Recruitments getRecruitment();
        String getParticipating();
    }
}
