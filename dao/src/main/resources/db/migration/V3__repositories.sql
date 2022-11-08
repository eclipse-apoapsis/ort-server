CREATE TABLE repositories
(
    id         BIGSERIAL PRIMARY KEY,
    type       TEXT                       NOT NULL,
    url        TEXT                       NOT NULL,
    product_id BIGINT REFERENCES products NOT NULL,
    CONSTRAINT unique_product_repository
        UNIQUE (url, product_id)
)
