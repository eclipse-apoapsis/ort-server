CREATE TABLE repositories
(
    id SERIAL PRIMARY KEY,
    type TEXT NOT NULL,
    url TEXT NOT NULL,
    fk_product SERIAL NOT NULL,
    CONSTRAINT fk_repositories_products
        FOREIGN KEY (fk_product)
            REFERENCES products(id),
    CONSTRAINT unique_product_repository
        UNIQUE (url, fk_product)
)
