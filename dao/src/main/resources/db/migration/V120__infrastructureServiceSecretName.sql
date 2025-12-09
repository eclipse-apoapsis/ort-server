-- This migration removes the foreign key constraint on the secrets and stores only the name of the secret.

ALTER TABLE infrastructure_services
    ADD COLUMN username_secret TEXT,
    ADD COLUMN password_secret TEXT;

UPDATE infrastructure_services
SET username_secret = secrets.name FROM secrets
WHERE infrastructure_services.username_secret_id = secrets.id;

UPDATE infrastructure_services
SET password_secret = secrets.name FROM secrets
WHERE infrastructure_services.password_secret_id = secrets.id;

ALTER TABLE infrastructure_services
    ALTER COLUMN username_secret SET NOT NULL,
    ALTER COLUMN password_secret SET NOT NULL;

ALTER TABLE infrastructure_services
    DROP CONSTRAINT IF EXISTS infrastructure_services_username_secret_id_fkey,
    DROP CONSTRAINT IF EXISTS infrastructure_services_password_secret_id_fkey;

ALTER TABLE infrastructure_services
    DROP COLUMN username_secret_id,
    DROP COLUMN password_secret_id;

DROP INDEX IF EXISTS infrastructure_services_all_value_columns;

CREATE INDEX infrastructure_services_all_value_columns
    ON infrastructure_services (name, url, description, username_secret, password_secret, credentials_type, organization_id, product_id, repository_id);
