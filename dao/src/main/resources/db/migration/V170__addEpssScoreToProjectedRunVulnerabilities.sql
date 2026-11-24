-- The highest EPSS score among the references of a vulnerability, or NULL if it has no EPSS reference. It is stored in
-- its own column, so that the vulnerabilities of a run can be sorted by it.
ALTER TABLE projected_run_vulnerabilities ADD COLUMN epss_score REAL NULL;

-- Vulnerabilities are usually sorted by descending EPSS score with the ones without a score last. An ascending index
-- read backwards would put NULLs first, so the index stores this order directly.
CREATE INDEX projected_run_vulnerabilities_epss_score_idx ON projected_run_vulnerabilities
    (ort_run_id, epss_score DESC NULLS LAST);
