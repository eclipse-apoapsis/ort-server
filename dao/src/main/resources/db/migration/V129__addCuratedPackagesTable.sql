-- This migration adds a materialized junction table for tracking which package
-- curations have been applied to packages for each ORT run.

CREATE TABLE curated_packages (
    id BIGSERIAL PRIMARY KEY,
    ort_run_id BIGINT NOT NULL REFERENCES ort_runs(id) ON DELETE CASCADE,
    package_id BIGINT NOT NULL REFERENCES packages(id) ON DELETE CASCADE,
    resolved_package_curation_id BIGINT NOT NULL REFERENCES resolved_package_curations(id) ON DELETE CASCADE,
    UNIQUE(ort_run_id, package_id, resolved_package_curation_id)
);
CREATE INDEX curated_packages_ort_run_id_idx ON curated_packages(ort_run_id);
CREATE INDEX curated_packages_package_id_idx ON curated_packages(package_id);
CREATE INDEX curated_packages_resolved_package_curation_id_idx ON curated_packages(resolved_package_curation_id);
