package com.geekersjoel237.koracore.shared.integration.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;

public final class KoraPostgres {

    private static final String MIGRATION_ROLE = "kora_migration";
    private static final String APP_ROLE = "kora_app";
    private static final Path ROLES_SCRIPT = Path.of("docker/postgres/initdb/10-kora-roles.sh");

    private KoraPostgres() {
    }

    public static PostgreSQLContainer<?> start() {
        PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withEnv("POSTGRES_MIGRATION_USER", MIGRATION_ROLE)
                .withEnv("POSTGRES_MIGRATION_PASSWORD", MIGRATION_ROLE)
                .withEnv("POSTGRES_APP_USER", APP_ROLE)
                .withEnv("POSTGRES_APP_PASSWORD", APP_ROLE)
                .withCopyFileToContainer(MountableFile.forHostPath(ROLES_SCRIPT, 0755),
                        "/docker-entrypoint-initdb.d/10-kora-roles.sh");
        postgres.start();
        return postgres;
    }

    public static void register(PostgreSQLContainer<?> postgres, DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_ROLE);
        registry.add("spring.datasource.password", () -> APP_ROLE);
        registry.add("spring.flyway.user", () -> MIGRATION_ROLE);
        registry.add("spring.flyway.password", () -> MIGRATION_ROLE);
        registry.add("spring.flyway.placeholders.app_role", () -> APP_ROLE);
    }

    public static JdbcTemplate asOwner(PostgreSQLContainer<?> postgres) {
        return new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), MIGRATION_ROLE, MIGRATION_ROLE));
    }

    public static SingleConnectionDataSource ownerTransaction(PostgreSQLContainer<?> postgres) {
        SingleConnectionDataSource dataSource =
                new SingleConnectionDataSource(postgres.getJdbcUrl(), MIGRATION_ROLE, MIGRATION_ROLE, true);
        dataSource.setAutoCommit(false);
        return dataSource;
    }
}
