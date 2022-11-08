CREATE TABLE repositories
(
    id         bigserial PRIMARY KEY,
    type       text                       NOT NULL,
    url        text                       NOT NULL,
    product_id bigint REFERENCES products NOT NULL,

    UNIQUE (url, product_id)
);
