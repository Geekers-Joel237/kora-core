package com.geekersjoel237.koracore.auth.adapters.out.security;

import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

// The JWT adapter's own table: what it remembers about the refresh tokens it issued.
@Component
class RefreshTokenStore {

    private final NamedParameterJdbcTemplate jdbc;

    RefreshTokenStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void record(Id tokenId, Id userId, Id familyId, Instant expiresAt, Instant issuedAt) {
        jdbc.update("""
                        INSERT INTO refresh_tokens (id, user_id, family_id, expires_at, created_at)
                        VALUES (:id, :userId, :familyId, :expiresAt, :createdAt)
                        """,
                Map.of("id", tokenId.value(),
                        "userId", userId.value(),
                        "familyId", familyId.value(),
                        "expiresAt", Timestamp.from(expiresAt),
                        "createdAt", Timestamp.from(issuedAt)));
    }

    boolean isLive(Id tokenId) {
        Integer count = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM refresh_tokens
                        WHERE id = :id AND revoked_at IS NULL
                        """,
                Map.of("id", tokenId.value()), Integer.class);
        return count != null && count > 0;
    }
}
