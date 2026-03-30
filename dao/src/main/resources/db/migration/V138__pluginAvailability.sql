ALTER TABLE plugins_read_model
    ADD COLUMN availability text NOT NULL DEFAULT 'ENABLED';

UPDATE plugins_read_model
SET availability = 'DISABLED'
WHERE enabled = false;

ALTER TABLE plugins_read_model DROP COLUMN enabled;
