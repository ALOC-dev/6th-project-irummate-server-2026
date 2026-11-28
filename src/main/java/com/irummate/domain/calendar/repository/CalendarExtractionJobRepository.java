package com.irummate.domain.calendar.repository;

import com.irummate.domain.calendar.entity.CalendarExtractionJob.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CalendarExtractionJobRepository {
    private final JdbcClient jdbc;

    public void enqueue(long noticeId, int bodyVersion) {
        jdbc.sql("""
                INSERT INTO calendar_extraction_jobs (notice_id, body_version, status, attempt_count, next_attempt_at)
                VALUES (:noticeId, :bodyVersion, 'PENDING', 0, CURRENT_TIMESTAMP)
                ON CONFLICT (notice_id, body_version) DO NOTHING
                """).param("noticeId", noticeId).param("bodyVersion", bodyVersion).update();
    }

    public Optional<ClaimedJob> claim(UUID lease) {
        return jdbc.sql("""
                WITH candidate AS (
                    SELECT id FROM calendar_extraction_jobs
                    WHERE (status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP)
                        OR (status = 'PROCESSING' AND lease_expires_at <= CURRENT_TIMESTAMP)
                    ORDER BY next_attempt_at, id LIMIT 1 FOR UPDATE SKIP LOCKED
                )
                UPDATE calendar_extraction_jobs j
                SET status = 'PROCESSING', lease_token = :lease,
                    lease_expires_at = CURRENT_TIMESTAMP + INTERVAL '5 minutes',
                    attempt_count = attempt_count + 1
                FROM candidate c, dormitory_notices n
                WHERE j.id = c.id AND n.id = j.notice_id
                RETURNING j.id, j.notice_id, j.body_version, j.attempt_count,
                    n.body_version AS current_version, n.title, n.published_at, n.body_text
                """).param("lease", lease).query((row, index) -> new ClaimedJob(
                row.getLong("id"), row.getLong("notice_id"), row.getInt("body_version"),
                row.getInt("attempt_count"), row.getInt("current_version"), row.getString("title"),
                row.getObject("published_at", LocalDate.class), row.getString("body_text"), lease)).optional();
    }

    public boolean finish(ClaimedJob job, Status status, String result, String error, int delaySeconds) {
        return jdbc.sql("""
                UPDATE calendar_extraction_jobs
                SET status = :status, result = CAST(:result AS jsonb), last_error = :error,
                    lease_token = NULL, lease_expires_at = NULL,
                    next_attempt_at = CURRENT_TIMESTAMP + :delaySeconds * INTERVAL '1 second'
                WHERE id = :id AND lease_token = :lease AND lease_expires_at > clock_timestamp()
                """).param("status", status.name()).param("result", result, Types.VARCHAR)
                .param("error", error, Types.VARCHAR).param("delaySeconds", delaySeconds)
                .param("id", job.id()).param("lease", job.lease()).update() == 1;
    }

    public Optional<PreviousResult> findPreviousResult(ClaimedJob job) {
        return jdbc.sql("""
                SELECT status, result::text AS result FROM calendar_extraction_jobs
                WHERE notice_id = :noticeId AND body_version < :version
                  AND status IN ('COMPLETED', 'REVIEW_REQUIRED', 'REVIEWED')
                ORDER BY body_version DESC LIMIT 1
                """).param("noticeId", job.noticeId()).param("version", job.bodyVersion())
                .query((row, index) -> new PreviousResult(
                        Status.valueOf(row.getString("status")), row.getString("result"))).optional();
    }

    public List<ReviewRow> findReviews(long afterId) {
        return jdbc.sql("""
                SELECT j.id, j.notice_id, j.body_version, n.title, n.url, j.result::text AS result, j.last_error
                FROM calendar_extraction_jobs j JOIN dormitory_notices n ON n.id = j.notice_id
                WHERE j.status = 'REVIEW_REQUIRED' AND j.body_version = n.body_version AND j.id > :afterId
                ORDER BY j.id LIMIT 50
                """).param("afterId", afterId).query((row, index) -> new ReviewRow(
                row.getLong("id"), row.getLong("notice_id"), row.getInt("body_version"), row.getString("title"),
                row.getString("url"), row.getString("result"), row.getString("last_error"))).list();
    }

    public boolean resolveReview(long jobId) {
        return jdbc.sql("""
                UPDATE calendar_extraction_jobs j SET status = 'REVIEWED'
                FROM dormitory_notices n
                WHERE j.id = :jobId AND j.status IN ('REVIEW_REQUIRED', 'REVIEWED')
                  AND n.id = j.notice_id AND n.body_version = j.body_version
                """).param("jobId", jobId).update() == 1;
    }

    public record ClaimedJob(long id, long noticeId, int bodyVersion, int attempts, int currentVersion,
                             String title, LocalDate publishedAt, String bodyText, UUID lease) {}

    public record ReviewRow(long id, long noticeId, int bodyVersion, String title, String url,
                            String result, String reason) {}

    public record PreviousResult(Status status, String result) {}
}
