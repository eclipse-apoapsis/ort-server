CREATE TABLE snippet_choices
(
    id                        bigserial PRIMARY KEY,
    given_location_start_line integer,
    given_location_end_line   integer,
    given_location_path       text NOT NULL,
    choice_purl               text NULL,
    choice_reason             text NOT NULL,
    choice_comment            text NULL,

    UNIQUE (given_location_start_line, given_location_end_line, given_location_path, choice_purl, choice_reason,
            choice_comment)
);

CREATE TABLE provenance_snippet_choices
(
    id         bigserial PRIMARY KEY,
    provenance text NOT NULL
);

CREATE INDEX provenance_snippet_choices_idx
    ON provenance_snippet_choices (provenance);

CREATE TABLE provenance_snippet_choices_snippet_choices
(
    provenance_snippet_choices_id bigint REFERENCES provenance_snippet_choices NOT NULL,
    snippet_choices_id            bigint REFERENCES snippet_choices            NOT NULL,

    PRIMARY KEY (provenance_snippet_choices_id, snippet_choices_id)
);

CREATE TABLE repository_configurations_provenance_snippet_choices
(
    repository_configuration_id   bigint REFERENCES repository_configurations  NOT NULL,
    provenance_snippet_choices_id bigint REFERENCES provenance_snippet_choices NOT NULL,

    PRIMARY KEY (repository_configuration_id, provenance_snippet_choices_id)
);
