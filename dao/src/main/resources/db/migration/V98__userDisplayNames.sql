CREATE TABLE user_display_names (
    user_id VARCHAR(40) PRIMARY KEY, -- Unique user identifier (from JWT `sub` claim, stable over time)
    username TEXT UNIQUE NOT NULL,   -- Preferred username (may change over time)
    full_name TEXT,                  -- User's full name (optional, may change over time, UX friendly)
    created_at TIMESTAMP NOT NULL
);

ALTER TABLE ort_runs
    ADD COLUMN user_id VARCHAR(40) NULL;
