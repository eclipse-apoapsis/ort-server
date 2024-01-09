ALTER TABLE infrastructure_services
    ADD COLUMN exclude_from_netrc boolean NOT NULL DEFAULT false;

DROP INDEX infrastructure_services_all_value_columns;

CREATE INDEX infrastructure_services_all_value_columns
    ON infrastructure_services (name, url, description, username_secret_id, password_secret_id, exclude_from_netrc);
