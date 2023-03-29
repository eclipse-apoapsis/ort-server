CREATE TABLE nested_provenances
(
    id                       bigserial PRIMARY KEY,
    root_vcs_id              bigint REFERENCES vcs_info         NOT NULL,
    root_resolved_revision   text                               NOT NULL,
    has_only_fixed_revisions boolean                            NOT NULL,

    UNIQUE (root_vcs_id, root_resolved_revision, has_only_fixed_revisions)
);

CREATE TABLE nested_provenance_sub_repositories
(
    id                   bigserial PRIMARY KEY,
    nested_provenance_id bigint REFERENCES nested_provenances NOT NULL,
    vcs_id               bigint REFERENCES vcs_info           NOT NULL,
    resolved_revision    text                                 NOT NULL,
    path                 text                                 NOT NULL,

    UNIQUE (id, vcs_id, resolved_revision)
);
