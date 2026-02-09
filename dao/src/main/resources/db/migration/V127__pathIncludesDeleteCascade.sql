-- This migration script modifies the foreign key constraints of the repository_configurations_path_includes table
-- to add an ON DELETE CASCADE clause. This was missed when the table was created; therefore, ORT runs with path
-- includes cannot be deleted.

ALTER TABLE repository_configurations_path_includes
  DROP CONSTRAINT fk_repository_configurations_path_includes_config_id,
  ADD CONSTRAINT fk_repository_configurations_path_includes_config_id
    FOREIGN KEY (repository_configuration_id) REFERENCES repository_configurations (id) ON DELETE CASCADE;
