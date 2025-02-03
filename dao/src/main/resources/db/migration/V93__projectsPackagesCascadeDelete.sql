-- This migration script modifies the foreign key constraints of the tables that reference projects and packages
-- to add an ON DELETE CASCADE clause. Cascade deletion is performed only on connecting (associating) tables to prevent
-- deletion of actual data. Orphaned data removal is performed by another process.
-- This script also creates necessary indexes to speed up queries.

ALTER TABLE packages_declared_licenses
    DROP CONSTRAINT packages_declared_licenses_package_id_fkey,
  ADD CONSTRAINT packages_declared_licenses_package_id_fkey
    FOREIGN KEY (package_id) REFERENCES packages (id) ON DELETE CASCADE;

ALTER TABLE packages_authors
    DROP CONSTRAINT packages_authors_package_id_fkey,
  ADD CONSTRAINT packages_authors_package_id_fkey
    FOREIGN KEY (package_id) REFERENCES packages (id) ON DELETE CASCADE;

ALTER TABLE projects_declared_licenses
    DROP CONSTRAINT projects_declared_licenses_project_id_fkey,
  ADD CONSTRAINT projects_declared_licenses_project_id_fkey
    FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE;

ALTER TABLE projects_authors
    DROP CONSTRAINT projects_authors_project_id_fkey,
  ADD CONSTRAINT projects_authors_project_id_fkey
    FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE;

ALTER TABLE processed_declared_licenses
    DROP CONSTRAINT processed_declared_licenses_package_id_fkey,
  ADD CONSTRAINT processed_declared_licenses_package_id_fkey
    FOREIGN KEY (package_id) REFERENCES packages (id) ON DELETE CASCADE;

ALTER TABLE processed_declared_licenses
    DROP CONSTRAINT processed_declared_licenses_project_id_fkey,
  ADD CONSTRAINT processed_declared_licenses_project_id_fkey
    FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE;


ALTER TABLE processed_declared_licenses_mapped_declared_licenses
    DROP CONSTRAINT processed_declared_licenses_m_processed_declared_license_i_fkey,
  ADD CONSTRAINT processed_declared_licenses_m_processed_declared_license_i_fkey
    FOREIGN KEY (processed_declared_license_id) REFERENCES processed_declared_licenses (id) ON DELETE CASCADE;


ALTER TABLE processed_declared_licenses_unmapped_declared_licenses
    DROP CONSTRAINT processed_declared_licenses_u_processed_declared_license_i_fkey,
  ADD CONSTRAINT processed_declared_licenses_u_processed_declared_license_i_fkey
    FOREIGN KEY (processed_declared_license_id) REFERENCES processed_declared_licenses (id) ON DELETE CASCADE;

-- Create necessary indexes to speed up queries
CREATE INDEX IF NOT EXISTS packages_analyzer_runs_package_id
    ON packages_analyzer_runs (package_id);

CREATE INDEX IF NOT EXISTS packages_analyzer_runs_analyzer_run_id
    ON packages_analyzer_runs (analyzer_run_id);

CREATE INDEX IF NOT EXISTS projects_analyzer_runs_package_id
    ON projects_analyzer_runs (project_id);

CREATE INDEX IF NOT EXISTS projects_analyzer_runs_analyzer_run_id
    ON projects_analyzer_runs (analyzer_run_id);
