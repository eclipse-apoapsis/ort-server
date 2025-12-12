-- This migration script adds indexes that are useful for migrations that
-- delete scan results and scan summaries, and will also be useful, when
-- support for removing specific results through the API is added.

CREATE INDEX IF NOT EXISTS scanner_runs_scan_results_fkey
    ON scanner_runs_scan_results (scan_result_id);

CREATE INDEX IF NOT EXISTS scan_results_scan_summary_fkey
    ON scan_results (scan_summary_id);
