CREATE TABLE maintenance_jobs
(
    id          bigserial PRIMARY KEY,
    name        text NOT NULL,
    status      text NOT NULL,
    started_at  timestamp,
    updated_at  timestamp,
    finished_at timestamp,
    data        jsonb
);
