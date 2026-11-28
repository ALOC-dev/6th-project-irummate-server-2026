package com.irummate.domain.calendar.repository;

import com.irummate.domain.calendar.entity.CalendarEvent;
import com.irummate.domain.calendar.entity.CalendarEvent.Category;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    @Query("""
            select e from CalendarEvent e left join fetch e.notice
            where e.deletedAt is null and e.details.startAt < :to
                and (e.details.endAt > :from or (e.details.endAt is null and e.details.startAt >= :from))
            order by e.details.startAt, e.id
            """)
    List<CalendarEvent> findOverlapping(@Param("from") Instant from, @Param("to") Instant to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from CalendarEvent e where e.id = :id and e.deletedAt is null")
    Optional<CalendarEvent> lockActive(@Param("id") long id);

    @Query("""
            select e from CalendarEvent e left join e.notice n
            where e.details.category = :category and e.details.startAt = :startAt
                and (e.deletedAt is null or n.id = :noticeId)
            """)
    List<CalendarEvent> findDuplicateCandidates(@Param("category") Category category,
                                                @Param("startAt") Instant startAt,
                                                @Param("noticeId") long noticeId);
}
