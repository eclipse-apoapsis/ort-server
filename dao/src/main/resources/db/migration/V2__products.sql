CREATE TABLE products
(
    id              BIGSERIAL PRIMARY KEY,
    name            TEXT                            NOT NULL,
    description     TEXT                            NULL,
    organization_id BIGINT REFERENCES organizations NOT NULL,

    UNIQUE (name, organization_id)
)
