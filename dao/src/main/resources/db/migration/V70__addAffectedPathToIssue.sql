-- Add affected_path column to issues table.
ALTER TABLE issues
    ADD COLUMN affected_path text NULL;

-- Set the affected path from the issue's message.
UPDATE issues
SET affected_path =
        regexp_replace(message, 'ERROR: Timeout after ([0-9]+) seconds while scanning file ''(.+)''.', '\2', 'g')
WHERE message ~ 'ERROR: Timeout after ([0-9]+) seconds while scanning file ''(.+)''.';
