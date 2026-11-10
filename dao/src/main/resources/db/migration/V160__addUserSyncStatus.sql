-- Store when the users were last synchronized with Keycloak successfully. The table holds at most one row.

CREATE TABLE user_sync_status
(
    id                INT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    last_succeeded_at TIMESTAMP NOT NULL
);
