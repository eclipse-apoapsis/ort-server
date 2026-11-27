-- The definitions of the project health metrics that advisors report. A definition describes how to read the values of
-- a metric and is the same for every package an advisor rates, so it is stored once and shared by all results that
-- report it.

CREATE TABLE health_metric_definitions
(
    id                 BIGSERIAL PRIMARY KEY,
    name               TEXT             NOT NULL,
    documentation      TEXT             NULL,
    documentation_link TEXT             NULL,
    min_value          DOUBLE PRECISION NULL,
    max_value          DOUBLE PRECISION NULL,
    higher_is_better   BOOLEAN          NULL
);

-- The SHA-256 hash of a text, or null for null. Unlike the digest function of the pgcrypto extension, which
-- V85__issuesIndexOnValueColumns.sql uses for the message of issues, it needs no extension, and with it no CREATE
-- privilege on the whole database. convert_to() is marked as stable, but with a fixed target encoding its result
-- depends only on the text and the encoding of the database, which never changes. So the function can be marked as
-- immutable, which an index requires.
CREATE OR REPLACE FUNCTION sha256_text(value TEXT) RETURNS BYTEA
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $$ SELECT sha256(convert_to(value, 'UTF8')) $$;

-- Keeps the definitions free of duplicates. The documentation and its link can be longer than an index entry allows,
-- so the index uses their hash instead, like V85__issuesIndexOnValueColumns.sql does for the message of issues.
CREATE UNIQUE INDEX health_metric_definitions_all_columns
ON health_metric_definitions (name, sha256_text(documentation), sha256_text(documentation_link), min_value, max_value,
    higher_is_better) NULLS NOT DISTINCT;

-- The summary of the health of a package as a whole, as reported by one advisor. An advisor result has at most one
-- summary. Its overall score is stored in health_metrics and references the summary.

CREATE TABLE health_summaries
(
    id                BIGSERIAL PRIMARY KEY,
    advisor_result_id BIGINT    NOT NULL UNIQUE REFERENCES advisor_results ON DELETE CASCADE,
    analyzed_at       TIMESTAMP NULL,
    analyzed_revision TEXT      NULL,
    tool_version      TEXT      NULL
);

-- The values of the project health metrics that an advisor reported for a package. A row belongs either to an advisor
-- result, as one of its metrics, or to a health summary, as its overall score. The value is null if the advisor could
-- not compute it, and the reason then says why. The reference value is the value the criticality was rated against.

CREATE TABLE health_metrics
(
    id                          BIGSERIAL PRIMARY KEY,
    advisor_result_id           BIGINT           NULL REFERENCES advisor_results ON DELETE CASCADE,
    health_summary_id           BIGINT           NULL UNIQUE REFERENCES health_summaries ON DELETE CASCADE,
    health_metric_definition_id BIGINT           NOT NULL REFERENCES health_metric_definitions,
    value                       DOUBLE PRECISION NULL,
    reference_value             DOUBLE PRECISION NULL,
    criticality                 TEXT             NULL,
    reason                      TEXT             NULL,
    details                     JSONB            NOT NULL DEFAULT '[]',

    CONSTRAINT health_metrics_owner_check CHECK (num_nonnulls(advisor_result_id, health_summary_id) = 1)
);

-- Used to load the metrics of the advisor results of a run.
CREATE INDEX health_metrics_advisor_result_id_idx ON health_metrics (advisor_result_id);
