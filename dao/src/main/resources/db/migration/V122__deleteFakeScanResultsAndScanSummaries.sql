-- This migration finds scan summaries that have issues whose message
-- indicates that the scan has actually failed completely, as previously
-- fake scan results and summaries were created and saved in case of
-- completely failing scans, and deletes these scan summaries and related
-- scan results and other data.

-- The summaries to delete are evaluated to not be connected to any
-- findings, which further ensures that they have indeed been "faked".

-- Create a temporary table with the scan summaries to delete. This is to
-- keep track of the scan summary ID's even when the rows are cleared from
-- the "scan_summaries_issues" table.

CREATE TEMP TABLE tmp_scan_summaries_to_delete AS
SELECT DISTINCT ssi.scan_summary_id
FROM scan_summaries_issues ssi
JOIN issues i ON i.id = ssi.issue_id
WHERE
    (
        i.message ILIKE '%exception%' OR
        i.message ILIKE '%fail%' OR
        i.message ILIKE '%missing%' OR
        i.message ILIKE '%unable%'
    )
    AND ssi.scan_summary_id NOT IN (
        SELECT lf.scan_summary_id FROM license_findings lf
    )
    AND ssi.scan_summary_id NOT IN (
        SELECT cf.scan_summary_id FROM copyright_findings cf
    )
    AND ssi.scan_summary_id NOT IN (
        SELECT sf.scan_summary_id FROM snippet_findings sf
    );

-- Delete scan results and connections to scanner runs for results that
-- are connected to the scan summaries to be deleted.

DELETE FROM scanner_runs_scan_results srsr
WHERE srsr.scan_result_id IN (
    SELECT sr.id
    FROM scan_results sr
    WHERE sr.scan_summary_id IN (
        SELECT t.scan_summary_id
        FROM tmp_scan_summaries_to_delete t
    )
);

DELETE FROM scan_results sr
WHERE sr.scan_summary_id IN (
    SELECT t.scan_summary_id
    FROM tmp_scan_summaries_to_delete t
);

-- Delete the scan summaries and all the related data.

DELETE FROM scan_summaries_issues ssi
WHERE ssi.scan_summary_id IN (
    SELECT t.scan_summary_id
    FROM tmp_scan_summaries_to_delete t
);

DELETE FROM scan_summaries ss
WHERE ss.id IN (
    SELECT t.scan_summary_id
    FROM tmp_scan_summaries_to_delete t
);

-- Drop the temp table.

DROP TABLE tmp_scan_summaries_to_delete;
