package com.geekersjoel237.koracore.shared.integration;

import com.geekersjoel237.koracore.shared.e2e.TestMailConfig;
import com.geekersjoel237.koracore.shared.integration.persistence.KoraPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestMailConfig.class)
class KoraCoreApplicationTests {

    static final PostgreSQLContainer<?> POSTGRES = KoraPostgres.start();

    @DynamicPropertySource
    static void overridePostgresProperties(DynamicPropertyRegistry registry) {
        KoraPostgres.register(POSTGRES, registry);
    }

    @Test
    void contextLoads() {
    }
}
