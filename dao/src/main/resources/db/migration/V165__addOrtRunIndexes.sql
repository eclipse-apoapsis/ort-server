-- Store the last assigned ORT run index for each repository, so that the next index can be allocated atomically with a
-- single upsert. Concurrent run creations for the same repository then wait for the row lock instead of failing with
-- serialization errors, as they did when the index was computed from the maximum index in the ort_runs table.

CREATE TABLE ort_run_indexes
(
    repository_id BIGINT PRIMARY KEY REFERENCES repositories ON DELETE CASCADE,
    last_index    BIGINT NOT NULL
);

INSERT INTO ort_run_indexes (repository_id, last_index)
SELECT repository_id, MAX(index)
FROM ort_runs
GROUP BY repository_id;
