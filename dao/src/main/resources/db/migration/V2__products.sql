CREATE TABLE products
(
    id              BIGSERIAL PRIMARY KEY,
    name            TEXT      NOT NULL,
    description     TEXT,
    fk_organization BIGSERIAL NOT NULL,
    CONSTRAINT fk_products_organizations
        FOREIGN KEY (fk_organization)
            REFERENCES organizations (id),
    CONSTRAINT unique_organization_product
        UNIQUE (name, fk_organization)
)
