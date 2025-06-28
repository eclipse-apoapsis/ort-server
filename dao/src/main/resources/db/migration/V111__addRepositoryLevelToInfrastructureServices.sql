ALTER TABLE infrastructure_services
    ADD COLUMN repository_id bigint REFERENCES repositories NULL;


-- An infrastructure service is either associated with an organization, a product or a repository.
-- The check below ensures that an infrastructure service cannot be associated with an organization, a product
-- or a repository at the same time.
ALTER TABLE infrastructure_services
DROP CONSTRAINT infrastructure_services_references_check;

ALTER TABLE infrastructure_services
    ADD CONSTRAINT infrastructure_services_references_check
        CHECK (
            (organization_id IS NOT NULL)::integer
    + (product_id IS NOT NULL)::integer
    + (repository_id IS NOT NULL)::integer
    <= 1
    );

DROP INDEX infrastructure_services_all_value_columns;

CREATE INDEX infrastructure_services_all_value_columns
    ON infrastructure_services (name, url, description, username_secret_id, password_secret_id, credentials_type, organization_id, product_id, repository_id);
