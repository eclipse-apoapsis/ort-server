-- Add a hash column to the scan_summary table to make it easy to find duplicates.

ALTER TABLE scan_summaries ADD COLUMN hash TEXT NOT NULL DEFAULT '';
