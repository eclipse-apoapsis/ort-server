CREATE TABLE infrastructure_services
(
    id                 bigserial PRIMARY KEY,
    name               text                            NOT NULL,
    url                text                            NOT NULL,
    description        text                            NULL,
    username_secret_id bigint REFERENCES secrets       NOT NULL,
    password_secret_id bigint REFERENCES secrets       NOT NULL,
    organization_id    bigint REFERENCES organizations NULL,
    product_id         bigint REFERENCES products      NULL,

    UNIQUE (name, organization_id, product_id)
);

-- An infrastructure service is either associated with an organization or a product. There is no association with
-- repositories, but with ORT runs - since the infrastructure services used by a repository are declared in a
-- configuration file contained in the repository and added dynamically.
-- The check below ensures that an infrastructure service cannot be associated with an organization and a product at
-- the same time; an additional association with a run (via the infrastructure_services_ort_runs table) is possible
-- though.
ALTER TABLE infrastructure_services
ADD CONSTRAINT infrastructure_services_references_check
CHECK (
    (organization_id IS NOT NULL)::integer
    + (product_id IS NOT NULL)::integer
    <= 1
);

CREATE INDEX infrastructure_services_all_value_columns
    ON infrastructure_services (name, url, description, username_secret_id, password_secret_id);

CREATE TABLE infrastructure_services_ort_runs
(
    infrastructure_service_id bigint REFERENCES infrastructure_services NOT NULL,
    ort_run_id                bigint REFERENCES ort_runs                NOT NULL,

    PRIMARY KEY (infrastructure_service_id, ort_run_id)
);
