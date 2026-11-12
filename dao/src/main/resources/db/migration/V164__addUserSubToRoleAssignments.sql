-- Store the ID of the user in Keycloak. The column user_id keeps the username. Assignments made before this change
-- have no ID until the user sync fills it in.

ALTER TABLE role_assignments
    ADD COLUMN user_sub TEXT NULL;

CREATE INDEX role_assignments_user_sub
    ON role_assignments (user_sub);
