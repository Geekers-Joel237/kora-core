package com.geekersjoel237.koracore.shared.e2e;

import com.geekersjoel237.koracore.shared.ports.out.mail.MailPort;
import com.geekersjoel237.koracore.shared.unit.doubles.InMemoryMailPort;
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
