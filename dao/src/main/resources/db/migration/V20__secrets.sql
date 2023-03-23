CREATE TABLE secrets
(
    id                bigserial PRIMARY KEY,
    path              text NOT NULL,
    name              text NULL,
    description       text NULL,
    organization_id   bigint REFERENCES organizations NULL,
    product_id        bigint REFERENCES products NULL,
    repository_id     bigint REFERENCES repositories NULL,

    UNIQUE (path),
    UNIQUE (name, organization_id, product_id, repository_id)
);

-- A single secret can only relate to one of the following: organization, product or repository.
-- The check below ensures this.

ALTER TABLE secrets
ADD CONSTRAINT secrets_type_check
CHECK (
    (organization_id IS NOT NULL)::integer
    + (product_id IS NOT NULL)::integer
    + (repository_id IS NOT NULL)::integer
    = 1
);
