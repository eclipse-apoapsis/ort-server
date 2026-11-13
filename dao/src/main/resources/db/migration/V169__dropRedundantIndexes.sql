-- Drop indexes whose columns are the leading columns of another index on the same table. The other index can serve
-- the same lookups, so the dropped indexes only slow down writes and use storage.

-- Covered by organizations_name_key (name).
DROP INDEX IF EXISTS organizations_name;

-- Covered by secrets_name_organization_id_product_id_repository_id_key (name, organization_id, product_id,
-- repository_id).
DROP INDEX IF EXISTS secrets_name;

-- Covered by role_assignments_user_id_organization_id_product_id_reposit_key (user_id, organization_id, product_id,
-- repository_id).
DROP INDEX IF EXISTS role_assignments_user_id;

-- Covered by role_assignments_hierarchy (organization_id, product_id, repository_id).
DROP INDEX IF EXISTS role_assignments_organization_id;

-- Covered by packages_analyzer_runs_pkey (package_id, analyzer_run_id).
DROP INDEX IF EXISTS packages_analyzer_runs_package_id;

-- Covered by packages_analyzer_runs_analyzer_run_id_package_id (analyzer_run_id, package_id).
DROP INDEX IF EXISTS packages_analyzer_runs_analyzer_run_id;

-- Covered by projects_analyzer_runs_pkey (project_id, analyzer_run_id).
DROP INDEX IF EXISTS projects_analyzer_runs_package_id;

-- Covered by packages_declared_licenses_pkey (package_id, declared_license_id).
DROP INDEX IF EXISTS packages_declared_licenses_package_id;

-- Covered by projects_declared_licenses_pkey (project_id, declared_license_id).
DROP INDEX IF EXISTS projects_declared_licenses_project_id;

-- Covered by snippet_findings_snippets_pkey (snippet_finding_id, snippet_id).
DROP INDEX IF EXISTS snippet_findings_snippets_snippet_finding_id;

-- Covered by scanner_runs_scanners_scanner_run_id_identifier_id_scanner__key (scanner_run_id, identifier_id,
-- scanner_name).
DROP INDEX IF EXISTS scanner_runs_scanners_run_id;

-- Covered by resolved_issues_ort_run_id_ort_run_issue_id_issue_resolutio_key (ort_run_id, ort_run_issue_id,
-- issue_resolution_id).
DROP INDEX IF EXISTS resolved_issues_ort_run_id_idx;

-- Covered by resolved_vulnerabilities_ort_run_id_vulnerability_id_identi_key (ort_run_id, vulnerability_id,
-- identifier_id, vulnerability_resolution_id).
DROP INDEX IF EXISTS resolved_vulnerabilities_ort_run_id_idx;

-- Covered by resolved_rule_violations_ort_run_id_rule_violation_id_rule__key (ort_run_id, rule_violation_id,
-- rule_violation_resolution_id).
DROP INDEX IF EXISTS resolved_rule_violations_ort_run_id_idx;

-- Covered by curated_packages_ort_run_id_package_id_resolved_package_cur_key (ort_run_id, package_id,
-- resolved_package_curation_id).
DROP INDEX IF EXISTS curated_packages_ort_run_id_idx;
