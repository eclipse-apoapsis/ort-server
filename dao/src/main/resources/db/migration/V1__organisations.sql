CREATE TABLE organizations
(
    id          BIGSERIAL PRIMARY KEY,
    name        TEXT UNIQUE NOT NULL,
    description TEXT        NULL
);
