ALTER TABLE infrastructure_services
    ADD COLUMN credentials_type text NULL;

DROP INDEX infrastructure_services_all_value_columns;

UPDATE infrastructure_services
    SET credentials_type = 'NETRC'
    WHERE exclude_from_netrc = false;

ALTER TABLE infrastructure_services
    DROP COLUMN exclude_from_netrc;

CREATE INDEX infrastructure_services_all_value_columns
    ON infrastructure_services (name, url, description, username_secret_id, password_secret_id, credentials_type);
