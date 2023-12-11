-- Add table to store per ORT run which packages have been scanned by which scanner.
CREATE TABLE scanner_runs_scanners
(
    id                    bigserial PRIMARY KEY,
    scanner_run_id        bigint    REFERENCES scanner_runs  NOT NULL,
    identifier_id         bigint    REFERENCES identifiers   NOT NULL,
    scanner_name          text                               NOT NULL,

    UNIQUE (scanner_run_id, identifier_id, scanner_name)
);

CREATE INDEX scanner_runs_scanners_run_id
    ON scanner_runs_scanners (scanner_run_id)
