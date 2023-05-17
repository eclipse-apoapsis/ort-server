CREATE TABLE storage
(
    id           bigserial PRIMARY KEY,
    created_at   timestamp NOT NULL,
    namespace    text      NOT NULL,
    key          text      NOT NULL,
    content_type text      NULL,
    size         integer   NOT NULL,
    data         oid       NOT NULL,

    UNIQUE (namespace, key)
);
