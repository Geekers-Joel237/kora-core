-- -----------------------------------------------------------------------------
-- Server-side trace of every refresh token issued (L-10).
--
-- A refresh token used to be its own proof: a valid signature was enough, for
-- seven days, and nothing could end it early. Each refresh token now names a row
-- here by its jti, and is accepted only while that row says it may be.
--
-- This table belongs to the JWT adapter, not to the domain: it is how this
-- issuer implements revocation, as an OAuth2 server would behind its own API.
--
--   family_id   every token descended from one login; ending a session, or
--               catching a replayed token, revokes the whole family
--   used_at     set when the token is exchanged for a new pair; a second use of
--               the same token is a replay
--   revoked_at  set by logout, or when a replay is detected
--
-- Access tokens are not stored: checking one per request would put a database
-- read on every call. They stay stateless and live 15 minutes.
--
-- Rows are never updated except to set used_at or revoked_at, and never deleted
-- by the application. Expired rows are dead weight and will need a purge.
-- -----------------------------------------------------------------------------

CREATE TABLE refresh_tokens (
    id          VARCHAR(255)             NOT NULL,
    user_id     VARCHAR(255)             NOT NULL,
    family_id   VARCHAR(255)             NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at     TIMESTAMP WITH TIME ZONE,
    revoked_at  TIMESTAMP WITH TIME ZONE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_refresh_tokens       PRIMARY KEY (id),
    CONSTRAINT fk_refresh_tokens_users FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_user_id   ON refresh_tokens (user_id);
