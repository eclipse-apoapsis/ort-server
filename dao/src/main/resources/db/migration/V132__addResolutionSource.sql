ALTER TABLE issue_resolutions
    ADD COLUMN source text DEFAULT 'REPOSITORY' NOT NULL;

DROP INDEX issue_resolutions_all_value_columns;

CREATE INDEX issue_resolutions_all_value_columns
    ON issue_resolutions (message, reason, comment, source);

ALTER TABLE rule_violation_resolutions
    ADD COLUMN source text DEFAULT 'REPOSITORY' NOT NULL;

DROP INDEX rule_violation_resolutions_all_value_columns;

CREATE INDEX rule_violation_resolutions_all_value_columns
    ON rule_violation_resolutions (message, reason, comment, source);

ALTER TABLE vulnerability_resolutions
    ADD COLUMN source text DEFAULT 'REPOSITORY' NOT NULL;

DROP INDEX vulnerability_resolutions_all_value_columns;

CREATE INDEX vulnerability_resolutions_all_value_columns
    ON vulnerability_resolutions (external_id, reason, comment, source);
