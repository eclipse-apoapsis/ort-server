ALTER TABLE analyzer_runs
    ADD COLUMN dependency_graphs jsonb DEFAULT '{}'::jsonb NOT NULL
