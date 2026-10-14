# Default Plugins

ORT Server uses plugins for many parts of an ORT run, for example package managers, advisors, scanners, and reporters.
_Default plugins_ define which plugins are enabled for a run if the run does not specify the plugins to use.
They are used in two places:

- The UI pre-selects the default plugins when a new run is created, and each plugin list in the form can be reset to the default plugins.
- When a run is created via the API, plugin lists that are not set in the job configurations are filled with the default plugins.

## Resolution of Default Plugins

Default plugins can be configured globally by instance administrators and per organization by organization administrators.
Default plugins are configured per category.
There is one category for each plugin type, except for scanners, which have separate categories for package scanners and project scanners.
The default plugins are resolved separately for each category in this order:

1. The default plugins configured for the organization of the repository.
2. The global default plugins.
3. The built-in default plugins.

The default plugins of an organization replace the global default plugins of the same category; they are not merged.
If an organization does not configure default plugins for a category, the global default plugins of that category are used.
If they are also not configured, the built-in default plugins are used.

### Built-in Default Plugins

If no default plugins are configured, these built-in defaults are used:

| Category                        | Built-in default plugins                          |
| ------------------------------- | ------------------------------------------------- |
| Package managers                | The package managers that ORT enables by default. |
| Package curation providers      | None                                              |
| Package configuration providers | None                                              |
| Advisors                        | `OSV`, `VulnerableCode`                           |
| Package scanners                | `ScanCode`                                        |
| Project scanners                | The built-in package scanners.                    |
| Reporters                       | `CycloneDX`, `SpdxDocument`, `WebApp`             |

### Package and Project Scanners

Scanners can be used to scan the packages of a repository, its projects, or both.
The default package scanners (category `SCANNER`) and the default project scanners (category `PROJECT_SCANNER`) are configured separately.
If no project scanners are configured on a level, the package scanners of the same level are also used to scan projects.
Therefore, the default project scanners are resolved in this order:

1. The project scanners configured for the organization.
2. The package scanners configured for the organization.
3. The global project scanners.
4. The global package scanners.
5. The built-in package scanners.

For example, if global project scanners are configured and an organization only configures package scanners, the package scanners of the organization are also used to scan projects.
To scan projects with different scanners, the organization has to configure project scanners as well.

### Order of Default Plugins

For package curation providers and package configuration providers, the order of the plugins matters.
Providers further up in the list take precedence.
Therefore, the default plugins of these categories are applied in the configured order.

### Availability of Default Plugins

Only plugins that are available for a repository are applied as default plugins.
Default plugins that are not available are ignored, for example, because they were disabled after they were configured as defaults, or because they are restricted and no plugin template makes them available for the organization.

For reporters, the report definitions from the admin configuration can also be used as default plugins.
The global default plugins are validated against the report definitions of the default config context.
If a run uses a config context that does not contain a configured report definition, the report definition is ignored.
A report definition is also ignored if the reporter it uses is not available for the organization.

## Configuring Default Plugins

### Global Default Plugins

Instance administrators can configure the global default plugins on the _Default Plugins_ page in the _Plugin Management_ section of the admin area.
For each category, the page shows whether the built-in defaults are used or whether they were customized.
Customized default plugins can be reset to the built-in defaults.

All plugins that are not disabled can be selected as global default plugins.

### Organization Default Plugins

Users with the `MANAGE_DEFAULT_PLUGINS` permission for an organization can configure the default plugins of the organization on the _Default Plugins_ page of the organization.
Organization administrators have this permission.
For each category, the page shows whether the global defaults, the built-in defaults, or the defaults of the organization are used.
Default plugins of the organization can be reset to inherit the global defaults again.

Only plugins that are available for the organization can be selected as default plugins of the organization.

### REST API

The default plugins can also be managed via the REST API:

| Endpoint                                                                    | Description                                                                     |
| --------------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| `GET /api/v1/admin/plugins/defaults`                                        | Get the global default plugins for all categories.                              |
| `GET /api/v1/admin/plugins/defaults/available`                              | Get the plugins that can be configured as global default plugins.               |
| `PUT /api/v1/admin/plugins/defaults/{category}`                             | Set the global default plugins for a category.                                  |
| `DELETE /api/v1/admin/plugins/defaults/{category}`                          | Reset the global default plugins for a category to the built-in defaults.       |
| `GET /api/v1/organizations/{organizationId}/plugins`                        | Get the plugins that are available for an organization.                         |
| `GET /api/v1/organizations/{organizationId}/plugins/defaults`               | Get the default plugins of an organization for all categories.                  |
| `PUT /api/v1/organizations/{organizationId}/plugins/defaults/{category}`    | Set the default plugins of an organization for a category.                      |
| `DELETE /api/v1/organizations/{organizationId}/plugins/defaults/{category}` | Reset the default plugins of an organization for a category to the global ones. |
| `GET /api/v1/repositories/{repositoryId}/plugins/defaults`                  | Get the resolved default plugins for a repository.                              |

The category is one of `PACKAGE_MANAGER`, `PACKAGE_CURATION_PROVIDER`, `PACKAGE_CONFIGURATION_PROVIDER`, `ADVISOR`, `SCANNER`, `PROJECT_SCANNER`, or `REPORTER`.
The `GET` endpoints for default plugins return the plugin IDs for each category together with the source of the defaults, which is one of `BUILT_IN`, `GLOBAL`, or `ORGANIZATION`.
If the default project scanners fall back to the package scanners, the `fallbackCategory` property is set to `SCANNER`.

## Default Plugins for Runs Created via the API

In the job configurations of a run, these plugin lists are filled with the default plugins if they are not set or set to `null`:

| Job configuration | Property                        | Category                        |
| ----------------- | ------------------------------- | ------------------------------- |
| `analyzer`        | `enabledPackageManagers`        | Package managers                |
| `analyzer`        | `packageCurationProviders`      | Package curation providers      |
| `advisor`         | `advisors`                      | Advisors                        |
| `scanner`         | `scanners`                      | Package scanners                |
| `scanner`         | `projectScanners`               | Project scanners                |
| `evaluator`       | `packageConfigurationProviders` | Package configuration providers |
| `reporter`        | `formats`                       | Reporters                       |
| `reporter`        | `packageConfigurationProviders` | Package configuration providers |

If `projectScanners` is not set, the scanners from `scanners` are also used to scan projects.
Therefore, the default project scanners are only applied if neither `scanners` nor `projectScanners` is set, and only if they do not fall back to the default package scanners.
If only `projectScanners` is set, `scanners` is filled with the default package scanners.
If only `scanners` is set, `projectScanners` is not changed, so the configured scanners are also used to scan projects.

To disable all plugins of a type, set the list to an empty list (`[]`) instead.
An exception are the enabled package managers: an empty list is treated like a list that is not set, because a run without package managers would not be useful.

Default plugins are only applied to the job configurations of jobs that are enabled.
For example, if a run does not contain a job configuration for the advisor, no advisors are run.
