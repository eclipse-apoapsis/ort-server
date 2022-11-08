CREATE TABLE products
(
    id              bigserial PRIMARY KEY,
    name            text                            NOT NULL,
    description     text                            NULL,
    organization_id bigint REFERENCES organizations NOT NULL,

    UNIQUE (name, organization_id)
);
