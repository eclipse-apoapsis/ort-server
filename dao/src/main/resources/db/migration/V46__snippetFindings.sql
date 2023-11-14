CREATE TABLE snippet_findings
(
    id              bigserial PRIMARY KEY,
    path            text                             NOT NULL,
    start_line      int                              NOT NULL,
    end_line        int                              NOT NULL,
    scan_summary_id bigint REFERENCES scan_summaries NOT NULL
);

CREATE TABLE snippets
(
    id              bigserial PRIMARY KEY,
    purl            text NOT NULL,
    artifact_id     bigint REFERENCES remote_artifacts NULL,
    vcs_id          bigint REFERENCES vcs_info NULL,
    path            text NOT NULL,
    start_line      int  NOT NULL,
    end_line        int  NOT NULL,
    license         text NOT NULL,
    score           real NOT NULL,
    additional_data jsonb NULL,

    UNIQUE (purl, artifact_id, vcs_id, path, start_line, end_line, license, score, additional_data)
);

CREATE TABLE snippet_findings_snippets
(
    snippet_finding_id bigint REFERENCES snippet_findings NOT NULL,
    snippet_id         bigint REFERENCES snippets         NOT NULL,

    PRIMARY KEY (snippet_finding_id, snippet_id)
);
