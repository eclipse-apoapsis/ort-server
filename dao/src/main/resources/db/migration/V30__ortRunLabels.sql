CREATE TABLE labels
(
    id                  bigserial PRIMARY KEY,
    key                 text                            NOT NULL,
    value               text                            NOT NULL,

    UNIQUE (key, value)
);

CREATE TABLE ort_runs_labels
(
    ort_run_id      bigint REFERENCES ort_runs       NOT NULL,
    label_id        bigint REFERENCES labels         NOT NULL,

    PRIMARY KEY (ort_run_id, label_id)
);
