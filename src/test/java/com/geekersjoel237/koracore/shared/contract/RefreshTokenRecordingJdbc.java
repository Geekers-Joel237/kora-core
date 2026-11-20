package com.geekersjoel237.koracore.shared.contract;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;

final class RefreshTokenRecordingJdbc extends NamedParameterJdbcTemplate {

    RefreshTokenRecordingJdbc() {
        super(new DriverManagerDataSource("jdbc:kora-contract:no-database"));
    }

    @Override
    public int update(String sql, Map<String, ?> paramMap) {
        if (sql.strip().startsWith("INSERT INTO refresh_tokens"))
            return 1;
        return super.update(sql, paramMap);
    }
}
