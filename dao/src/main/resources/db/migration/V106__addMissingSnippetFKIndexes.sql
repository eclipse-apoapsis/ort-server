-- This migration adds indexes on foreign key columns on tables related to snippets. This is needed to be
-- able to delete these structures efficiently.

CREATE INDEX IF NOT EXISTS snippet_findings_snippets_snippet_finding_id ON snippet_findings_snippets (snippet_finding_id);
CREATE INDEX IF NOT EXISTS snippet_findings_snippets_snippet_id ON snippet_findings_snippets (snippet_id);
