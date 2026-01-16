-- This migration adds materialized junction tables for tracking which resolutions
-- have been applied to issues, vulnerabilities, and rule violations for each ORT run.
-- These tables enable efficient querying of resolved items without re-computing
-- resolution matches at query time.

-- Materialized junction table for resolved issues
CREATE TABLE resolved_issues (
    id BIGSERIAL PRIMARY KEY,
    ort_run_id BIGINT NOT NULL REFERENCES ort_runs(id) ON DELETE CASCADE,
    ort_run_issue_id BIGINT NOT NULL REFERENCES ort_runs_issues(id) ON DELETE CASCADE,
    issue_resolution_id BIGINT NOT NULL REFERENCES issue_resolutions(id) ON DELETE CASCADE,
    UNIQUE(ort_run_id, ort_run_issue_id, issue_resolution_id)
);
CREATE INDEX resolved_issues_ort_run_id_idx ON resolved_issues(ort_run_id);
CREATE INDEX resolved_issues_ort_run_issue_id_idx ON resolved_issues(ort_run_issue_id);

-- Materialized junction table for resolved vulnerabilities
CREATE TABLE resolved_vulnerabilities (
    id BIGSERIAL PRIMARY KEY,
    ort_run_id BIGINT NOT NULL REFERENCES ort_runs(id) ON DELETE CASCADE,
    vulnerability_id BIGINT NOT NULL REFERENCES vulnerabilities(id) ON DELETE CASCADE,
    identifier_id BIGINT NOT NULL REFERENCES identifiers(id) ON DELETE CASCADE,
    vulnerability_resolution_id BIGINT NOT NULL REFERENCES vulnerability_resolutions(id) ON DELETE CASCADE,
    UNIQUE(ort_run_id, vulnerability_id, identifier_id, vulnerability_resolution_id)
);
CREATE INDEX resolved_vulnerabilities_ort_run_id_idx ON resolved_vulnerabilities(ort_run_id);

-- Materialized junction table for resolved rule violations
CREATE TABLE resolved_rule_violations (
    id BIGSERIAL PRIMARY KEY,
    ort_run_id BIGINT NOT NULL REFERENCES ort_runs(id) ON DELETE CASCADE,
    rule_violation_id BIGINT NOT NULL REFERENCES rule_violations(id) ON DELETE CASCADE,
    rule_violation_resolution_id BIGINT NOT NULL REFERENCES rule_violation_resolutions(id) ON DELETE CASCADE,
    UNIQUE(ort_run_id, rule_violation_id, rule_violation_resolution_id)
);
CREATE INDEX resolved_rule_violations_ort_run_id_idx ON resolved_rule_violations(ort_run_id);
CREATE INDEX resolved_rule_violations_rule_violation_id_idx ON resolved_rule_violations(rule_violation_id);
