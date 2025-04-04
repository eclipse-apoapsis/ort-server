-- Content Management: Markdown-formatted text to be displayed in UI sections.
CREATE TABLE content_management_sections (
    id TEXT PRIMARY KEY,
    is_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    markdown TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

INSERT INTO content_management_sections (id, is_enabled, markdown, updated_at)
VALUES (
           'footer',
           false,
           $markdown$
Please use layout markers like this:

Content inside a "align-left" layout marker is aligned to the left of the footer.
Content inside a "align-right" layout marker is aligned to the right of the footer.

::: align-left
**ORT Server**
A scalable application to automate software compliance checks.
:::

::: align-right
[API](https://eclipse-apoapsis.github.io/ort-server/api/ort-server-api)
:::

::: align-right
[Issues](https://github.com/eclipse-apoapsis/ort-server/issues)
:::

::: align-right
[Visit on Github.com](https://github.com/eclipse-apoapsis/ort-server)
:::
    $markdown$,
    NOW()
);

