package com.geekersjoel237.koracore.shared.contract;

import com.geekersjoel237.koracore.auth.config.SecurityConfig;
import com.geekersjoel237.koracore.auth.config.SecurityProperties;
import com.geekersjoel237.koracore.auth.ports.out.security.TokenIssuer;
import com.geekersjoel237.koracore.shared.config.ClockConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@TestConfiguration
@Import({SecurityConfig.class, ClockConfig.class})
@EnableConfigurationProperties(SecurityProperties.class)
@ComponentScan(basePackages = "com.geekersjoel237.koracore.auth.adapters.out.security")
@ComponentScan(basePackages = "com.geekersjoel237.koracore", useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                pattern = "com\\.geekersjoel237\\.koracore\\.\\w+\\.adapters\\.in\\.rest\\.mapping\\..*"))
class ContractSlice {

    @Bean
    @Primary
    ProgrammableCommandBus programmableCommandBus() {
        return new ProgrammableCommandBus();
    }

    @Bean
    NamedParameterJdbcTemplate refreshTokenJdbc() {
        return new RefreshTokenRecordingJdbc();
    }

    @Bean
    ContractTokens contractTokens(TokenIssuer issuer) {
        return new ContractTokens(issuer);
    }
}
