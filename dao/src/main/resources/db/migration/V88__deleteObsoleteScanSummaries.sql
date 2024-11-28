-- This migration script removes duplicate and obsolete entries from the scan_summaries table and its child tables.
-- This is a follow-up of migration V87: The scan summaries associated with the duplicate scan results can now be
-- removed as well.

-- Create temporary indexes for foreign key relations. This is a prerequisite to run the following DELETE statements
-- within a reasonable time frame.

CREATE INDEX scan_summaries_issues_scan_summary_fkey
  ON scan_summaries_issues (scan_summary_id);

CREATE INDEX scan_results_scan_summary_fkey
  ON scan_results (scan_summary_id);

-- Delete all the data related to scan summaries that are no longer associated with any scan results.

DELETE FROM scan_summaries_issues ssi
WHERE ssi.scan_summary_id NOT IN (
  SELECT sr.scan_summary_id FROM scan_results sr
);

DELETE FROM license_findings lf
WHERE lf.scan_summary_id NOT IN (
  SELECT sr.scan_summary_id FROM scan_results sr
);

DELETE FROM copyright_findings cf
WHERE cf.scan_summary_id NOT IN (
  SELECT sr.scan_summary_id FROM scan_results sr
);

DELETE from snippet_findings_snippets
WHERE snippet_findings_snippets.snippet_finding_id in (
  SELECT sfs.snippet_finding_id
  FROM snippet_findings_snippets sfs
  INNER JOIN snippet_findings sf ON sfs.snippet_finding_id = sf.id
  WHERE sf.scan_summary_id NOT IN (
    SELECT sr.scan_summary_id FROM scan_results sr
  )
);

DELETE FROM snippet_findings sf
WHERE sf.scan_summary_id NOT IN (
  SELECT sr.scan_summary_id FROM scan_results sr
);

DELETE FROM scan_summaries ss
WHERE ss.id NOT IN (
  SELECT sr.scan_summary_id FROM scan_results sr
);

-- Cleanup.

DROP INDEX scan_summaries_issues_scan_summary_fkey;

DROP INDEX scan_results_scan_summary_fkey;
