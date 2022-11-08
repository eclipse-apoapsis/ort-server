CREATE TABLE repositories
(
    id         BIGSERIAL PRIMARY KEY,
    type       TEXT                       NOT NULL,
    url        TEXT                       NOT NULL,
    product_id BIGINT REFERENCES products NOT NULL,

    UNIQUE (url, product_id)
);
