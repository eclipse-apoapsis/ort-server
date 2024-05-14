-- Replace the user information in a repository URL with 'xxx'. If multiple repositories have the same
-- URL after the replacement, append a numerical counter after the user information (e.g., 'xxx2', 'xxx3').

DO $$
DECLARE
    repository record;
    base_url text;
    new_url text;
    counter integer;
BEGIN
    FOR repository IN
        SELECT id, url, product_id
        FROM repositories
        WHERE url ~ '://[^/]+@'
    LOOP
        base_url := regexp_replace(repository.url, '://[^/]+@', '://xxx@');
        new_url := base_url;
        counter := 1;

        LOOP
            BEGIN
                UPDATE repositories
                SET url = new_url
                WHERE id = repository.id;

                EXIT;
            EXCEPTION
                WHEN unique_violation THEN
                    counter := counter + 1;
                    new_url := regexp_replace(base_url, '@', counter || '@');
            END;
        END LOOP;
    END LOOP;
END;
$$;
