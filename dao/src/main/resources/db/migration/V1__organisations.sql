CREATE TABLE organizations
(
    id          bigserial PRIMARY KEY,
    name        text UNIQUE NOT NULL,
    description text        NULL
);
