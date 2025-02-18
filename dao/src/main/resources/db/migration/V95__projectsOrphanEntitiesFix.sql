-- This migration script modifies the foreign key constraint on project_scopes table that reference projects table.

ALTER TABLE project_scopes
    DROP CONSTRAINT project_scopes_project_id_fkey,
  ADD CONSTRAINT project_scopes_project_id_fkey
    FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE;
