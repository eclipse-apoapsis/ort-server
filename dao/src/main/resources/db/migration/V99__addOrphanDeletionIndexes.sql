-- This migration adds indexes on foreign key columns on tables that are processed by the task to delete orphaned
-- entities. This is needed to speed up the corresponding DELETE statements.

-- Foreign key indexes for the packages table.
CREATE INDEX IF NOT EXISTS packages_declared_licenses_package_id ON packages_declared_licenses (package_id);
CREATE INDEX IF NOT EXISTS shortest_dependency_paths_package_id ON shortest_dependency_paths (package_id);

-- Foreign key indexes for the projects table.
CREATE INDEX IF NOT EXISTS project_scopes_project_id ON project_scopes (project_id);
CREATE INDEX IF NOT EXISTS projects_authors_project_id ON projects_authors (project_id);
CREATE INDEX IF NOT EXISTS projects_declared_licenses_project_id ON projects_declared_licenses (project_id);
CREATE INDEX IF NOT EXISTS shortest_dependency_paths_project_id ON shortest_dependency_paths (project_id);
