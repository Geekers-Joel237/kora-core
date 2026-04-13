package com.geekersjoel237.koracore.e2e;

import com.geekersjoel237.koracore.domain.port.MailPort;
import com.geekersjoel237.koracore.shared.inmemory.InMemoryMailPort;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestMailConfig {

    @Bean
    @Primary
    public MailPort mailPort() {
        return new InMemoryMailPort();
    }
}
