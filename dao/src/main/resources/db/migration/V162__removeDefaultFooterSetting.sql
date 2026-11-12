-- The default footer is defined in code by `ServerSettingKey.FOOTER` and used if no footer is stored. Remove the
-- footer that was inserted by V100 and moved to the server settings by V148 if it was not changed, so that changes to
-- the default in code take effect.
DELETE FROM server_settings
WHERE key = 'FOOTER'
  AND is_enabled = false
  AND btrim(value, E' \n') = btrim($markdown$
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
$markdown$, E' \n');
