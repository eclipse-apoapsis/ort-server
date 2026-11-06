-- Store when a user was last updated, when they were deleted in Keycloak and when they were last synchronized with
-- Keycloak. Rows are never deleted, because runs reference them for auditing.

ALTER TABLE users
    ADD COLUMN updated_at     TIMESTAMP NULL,
    ADD COLUMN deleted_at     TIMESTAMP NULL,
    ADD COLUMN last_synced_at TIMESTAMP NULL;

-- Until now, the creation time was overwritten on every update, so it holds the time of the last update.
UPDATE users SET updated_at = created_at;

ALTER TABLE users ALTER COLUMN updated_at SET NOT NULL;
