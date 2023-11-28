-- Add indexes to speed up finding the data associated to a scan summary.
CREATE INDEX copyright_findings_scan_summary_id ON copyright_findings (scan_summary_id);
CREATE INDEX license_findings_scan_summary_id ON license_findings (scan_summary_id);
CREATE INDEX snippet_findings_scan_summary_id ON snippet_findings (scan_summary_id);
