-- Drop foreign key constraints from infrastructure_services to secrets.
ALTER TABLE infrastructure_services
    DROP CONSTRAINT infrastructure_services_password_secret_id_fkey;

ALTER TABLE infrastructure_services
    DROP CONSTRAINT infrastructure_services_username_secret_id_fkey;
