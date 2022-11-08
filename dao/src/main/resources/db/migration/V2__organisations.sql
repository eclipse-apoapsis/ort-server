CREATE TABLE organizations
(
    id          serial PRIMARY KEY,
    name        TEXT UNIQUE NOT NULL,
    description TEXT
);
