package com.geekersjoel237.koracore.auth.adapters.out.security;

import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    void revokeFamilyOf(Id tokenId, Instant at) {
        jdbc.update("""
                        UPDATE refresh_tokens SET revoked_at = :at
                        WHERE revoked_at IS NULL
                          AND family_id = (SELECT family_id FROM refresh_tokens WHERE id = :id)
                        """,
                Map.of("id", tokenId.value(), "at", Timestamp.from(at)));
    }

    // A replay is answered with a refusal, and the refusal rolls the caller's transaction
    // back: the revocation must not go with it. It commits on its own, as it would on a
    // remote authorization server.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void revokeFamilyOfReplayed(Id tokenId, Instant at) {
        revokeFamilyOf(tokenId, at);
    }

    Optional<Id> consume(Id tokenId, Instant at) {
        List<String> family = jdbc.queryForList("""
                        UPDATE refresh_tokens SET used_at = :at
                        WHERE id = :id AND used_at IS NULL AND revoked_at IS NULL
                        RETURNING family_id
                        """,
                Map.of("id", tokenId.value(), "at", Timestamp.from(at)), String.class);
        return family.stream().findFirst().map(Id::new);
    }

    boolean wasUsed(Id tokenId) {
        Integer count = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM refresh_tokens
                        WHERE id = :id AND used_at IS NOT NULL
                        """,
                Map.of("id", tokenId.value()), Integer.class);
        return count != null && count > 0;
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
