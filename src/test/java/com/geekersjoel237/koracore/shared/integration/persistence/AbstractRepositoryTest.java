package com.geekersjoel237.koracore.shared.integration.persistence;

import com.geekersjoel237.koracore.shared.e2e.TestMailConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@Import(TestMailConfig.class)
public abstract class AbstractRepositoryTest {

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = KoraPostgres.start();
    }

    @DynamicPropertySource
    static void overridePostgresProperties(DynamicPropertyRegistry registry) {
        KoraPostgres.register(POSTGRES, registry);
    }

    protected static SingleConnectionDataSource ownerTransaction() {
        return KoraPostgres.ownerTransaction(POSTGRES);
    }
}