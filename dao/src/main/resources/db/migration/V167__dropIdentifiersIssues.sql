-- Since V78, the issues created by the Analyzer are linked to ORT runs and identifiers via the `ort_runs_issues` table,
-- and V78 copied all existing links into that table. Since then, the `identifiers_issues` table has been neither
-- written nor read.

DROP TABLE IF EXISTS identifiers_issues;
