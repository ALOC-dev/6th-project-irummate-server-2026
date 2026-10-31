package com.irummate.domain.notice.repository;

import com.irummate.domain.notice.entity.DormitoryNotice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface NoticeRepository extends JpaRepository<DormitoryNotice, Long> {
    Page<DormitoryNotice> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);

    // 반환 행 없음=변경 없음, true=신규, false=갱신 (PostgreSQL 17).
    // RETURNING 결과를 읽으므로 @Modifying을 사용하지 않는다.
    @Query(value = """
            INSERT INTO dormitory_notices AS existing
                (external_id, title, published_at, url, created_at, updated_at)
            VALUES (:externalId, :title, :publishedAt, :url, clock_timestamp(), clock_timestamp())
            ON CONFLICT (external_id) DO UPDATE
            SET title = EXCLUDED.title, published_at = EXCLUDED.published_at,
                url = EXCLUDED.url, updated_at = clock_timestamp()
            WHERE (existing.title, existing.published_at, existing.url)
                IS DISTINCT FROM (EXCLUDED.title, EXCLUDED.published_at, EXCLUDED.url)
            RETURNING (xmax = 0) AS inserted
            """, nativeQuery = true)
    List<Boolean> upsert(@Param("externalId") String externalId, @Param("title") String title,
                         @Param("publishedAt") LocalDate publishedAt, @Param("url") String url);
}
