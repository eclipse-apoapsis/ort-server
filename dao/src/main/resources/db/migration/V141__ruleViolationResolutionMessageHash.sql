ALTER TABLE rule_violation_resolutions
    ADD COLUMN IF NOT EXISTS message_hash text;

DROP INDEX IF EXISTS rule_violation_resolutions_all_value_columns;

CREATE INDEX IF NOT EXISTS rule_violation_resolutions_server_value_columns
    ON rule_violation_resolutions (message_hash, reason, comment, source)
    WHERE source = 'SERVER';

CREATE INDEX IF NOT EXISTS rule_violation_resolutions_non_server_value_columns
    ON rule_violation_resolutions (message, reason, comment, source)
    WHERE source <> 'SERVER';
