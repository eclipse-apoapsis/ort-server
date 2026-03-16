UPDATE issue_resolutions
SET source = 'REPOSITORY_FILE'
WHERE source = 'REPOSITORY';

ALTER TABLE issue_resolutions
    ALTER COLUMN source SET DEFAULT 'REPOSITORY_FILE';

UPDATE rule_violation_resolutions
SET source = 'REPOSITORY_FILE'
WHERE source = 'REPOSITORY';

ALTER TABLE rule_violation_resolutions
    ALTER COLUMN source SET DEFAULT 'REPOSITORY_FILE';

UPDATE vulnerability_resolutions
SET source = 'REPOSITORY_FILE'
WHERE source = 'REPOSITORY';

ALTER TABLE vulnerability_resolutions
    ALTER COLUMN source SET DEFAULT 'REPOSITORY_FILE';
