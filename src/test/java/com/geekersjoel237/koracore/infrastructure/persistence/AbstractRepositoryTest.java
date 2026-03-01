package com.geekersjoel237.koracore.infrastructure.persistence;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared base for all repository integration tests.
 *
 * Uses the Singleton Container Pattern: a single PostgreSQL container is started
 * once in a static initializer and lives for the entire JVM lifetime.
 * @DynamicPropertySource wires the container's URL into the Spring context so
 * that all subclasses share the same Spring context (context cache hit).
 *
 * @Transactional ensures each test method runs in its own transaction that is
 * rolled back after the method completes — no manual cleanup required.
 */
@SpringBootTest
@Transactional
public abstract class AbstractRepositoryTest {

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void overridePostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}