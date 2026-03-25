ALTER TABLE issue_resolutions
    ADD COLUMN IF NOT EXISTS message_hash text;

DROP INDEX IF EXISTS issue_resolutions_all_value_columns;

CREATE INDEX IF NOT EXISTS issue_resolutions_server_value_columns
    ON issue_resolutions (message_hash, reason, comment, source)
    WHERE source = 'SERVER';

CREATE INDEX IF NOT EXISTS issue_resolutions_non_server_value_columns
    ON issue_resolutions (message, reason, comment, source)
    WHERE source <> 'SERVER';
