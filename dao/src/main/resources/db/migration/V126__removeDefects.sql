-- Remove all defects related tables.

DROP TABLE defect_labels;
DROP TABLE advisor_results_defects;
DROP TABLE defects;

ALTER TABLE advisor_results DROP COLUMN capabilities;
