package com.irummate.notice;

import com.irummate.domain.notice.dto.*;
import com.irummate.domain.notice.entity.DormitoryNotice;
import com.irummate.domain.notice.repository.NoticeRepository;
import com.irummate.domain.notice.service.NoticeService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Runs only against an explicitly supplied disposable PostgreSQL database named notice_test. */
@EnabledIfEnvironmentVariable(named = "NOTICE_TEST_DB_URL", matches = "jdbc:postgresql://[^/]+/notice_test(?:\\?.*)?")
@SpringBootTest(classes = NoticePostgresTest.Config.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NoticePostgresTest {
    private static final String SCHEMA = "notice_test_" + UUID.randomUUID().toString().replace("-", "");
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = DormitoryNotice.class)
    @EnableJpaRepositories(basePackageClasses = NoticeRepository.class)
    @Import(NoticeService.class)
    static class Config { }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        try (var connection = DriverManager.getConnection(url(), user(), password());
             var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
        }
        registry.add("spring.config.import", () -> "");
        registry.add("spring.datasource.url", NoticePostgresTest::url);
        registry.add("spring.datasource.username", NoticePostgresTest::user);
        registry.add("spring.datasource.password", NoticePostgresTest::password);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }

    private static String url() { return System.getenv("NOTICE_TEST_DB_URL"); }
    private static String user() { return System.getenv().getOrDefault("NOTICE_TEST_DB_USERNAME", "postgres"); }
    private static String password() { return System.getenv().getOrDefault("NOTICE_TEST_DB_PASSWORD", "postgres"); }

    @Autowired NoticeService service;
    @Autowired NoticeRepository repository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void clearOnlyGeneratedTestSchema() {
        jdbc.execute("TRUNCATE TABLE " + SCHEMA + ".dormitory_notices RESTART IDENTITY");
    }

    @AfterAll static void removeGeneratedTestSchema() throws Exception {
        try (var connection = DriverManager.getConnection(url(), user(), password());
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
        }
    }

    private void insert(String externalId, String title, LocalDate publishedAt) {
        jdbc.update("""
                INSERT INTO dormitory_notices (external_id, title, published_at, url, created_at, updated_at)
                VALUES (?, ?, ?, ?, clock_timestamp(), clock_timestamp())
                """, externalId, title, java.sql.Date.valueOf(publishedAt),
                "https://dormitory.uos.ac.kr/dormitory/web/bbs/dormitory_notice/" + externalId);
    }

    @Test void sortsByDateThenIdAndPaginates() {
        insert("1", "older", LocalDate.of(2026, 1, 1));
        insert("2", "입사 공지", LocalDate.of(2026, 10, 27));
        insert("3", "입사 100%", LocalDate.of(2026, 10, 27));
        var first = service.findNotices(0, 2, null);
        assertEquals(List.of("입사 100%", "입사 공지"), first.getContent().stream().map(NoticeResponseDto::getTitle).toList());
        assertEquals(3, first.getTotalElements());
        assertEquals(2, first.getTotalPages());
        var second = service.findNotices(1, 2, null);
        assertEquals(1, second.getPage());
        assertEquals("older", second.getContent().getFirst().getTitle());
        assertTrue(service.findNotices(2, 2, null).getContent().isEmpty());
    }

    @Test void searchesTitleCaseInsensitivelyAndEscapesWildcards() {
        insert("1", "Dormitory 입사 공지", LocalDate.of(2026, 10, 27));
        insert("2", "입사 100%", LocalDate.of(2026, 10, 27));
        assertEquals(2, service.findNotices(0, 10, "입사").getTotalElements());
        assertEquals(1, service.findNotices(0, 10, "dormitory").getTotalElements());
        assertEquals(1, service.findNotices(0, 10, "%").getTotalElements());
        assertEquals(0, service.findNotices(0, 10, "absent").getTotalElements());
    }

    @Test void additiveMigrationCanRunTwiceWithoutChangingData() {
        insert("1", "보존", LocalDate.of(2026, 10, 27));
        var script = new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.FileSystemResource("migration-dormitory-notices.sql"));
        script.execute(java.util.Objects.requireNonNull(jdbc.getDataSource()));
        script.execute(jdbc.getDataSource());
        assertEquals(1, repository.count());
        assertEquals("보존", service.findNotices(0, 10, null).getContent().getFirst().getTitle());
    }

    @Test void emptyDatabaseReturnsEmptyPage() {
        var page = service.findNotices(0, 10, null);
        assertTrue(page.getContent().isEmpty());
        assertEquals(0, page.getTotalElements());
        assertEquals(0, page.getTotalPages());
    }
}
