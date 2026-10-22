# Admin Configuration

The admin configuration is a central configuration file that defines settings which apply to all ORT runs of an ORT Server instance and which end users cannot change when they trigger a run.
It covers, among other things, the rule sets used by the Evaluator, the reports offered by the Reporter, the infrastructure used by the Notifier, and a global Maven Central mirror.

:::note
The admin configuration is different from the [static configuration](config.md) of the ORT Server components.
The static configuration controls the deployment itself, such as database connections or message queues, and is provided through environment variables.
The admin configuration controls how ORT runs are processed and is read from the configuration file provider at runtime.
:::

## Location and Loading

ORT Server reads the admin configuration through the configured [`ConfigFileProvider`](https://github.com/eclipse-apoapsis/ort-server/blob/main/config/README.md), the same mechanism that provides other configuration files such as evaluator rules or reporter templates.
Depending on the deployment, this can be a Git repository, a GitHub repository, or a local directory.

By default, the file is expected at the path `ort-server.conf` relative to the root of the configuration file provider.
To use a different path, set the `adminConfigPath` property in the application configuration of the ORT Server components, for example as the JVM system property `-DadminConfigPath=config/ort-server.conf`.
Ensure that all components that read the admin configuration use the same path.

The file is loaded for each ORT run using the run's resolved configuration context.
If the configuration file provider supports versioning, for example by using Git branches or revisions, different runs can therefore use different versions of the admin configuration, and all workers of a single run are guaranteed to see the same version.

ORT Server handles a missing file depending on the effective path:

- If the effective path is the default path `ort-server.conf` and no file exists there, ORT Server logs a warning and falls back to an empty configuration in which all settings have their default values.
  This also applies if `adminConfigPath` is explicitly set to `ort-server.conf`.
  This mode is mainly useful for testing; production deployments should always provide an admin configuration.
- If the effective path is any other path and no file exists there, loading fails and the affected ORT runs fail.
  To make sure that a missing admin configuration is detected, use a path other than the default.

## File Format

The admin configuration uses the [HOCON](https://github.com/lightbend/config/blob/main/HOCON.md) format, a superset of JSON that supports comments, unquoted keys, and nested blocks.
All sections and properties are optional; any property that is omitted takes its default value.

The top level of the file consists of the following sections:

| Section              | Description                                                                            |
| -------------------- | -------------------------------------------------------------------------------------- |
| `defaultRuleSet`     | The rule set used when an ORT run does not request a specific one.                     |
| `ruleSets`           | Named rule sets that users can select when triggering an ORT run.                      |
| `scanner`            | Settings for the Scanner worker.                                                       |
| `reporter`           | Report definitions, assets, and other settings for the Reporter worker.                |
| `notifier`           | The notification script and the mail and Jira servers used by the Notifier worker.     |
| `mavenCentralMirror` | A mirror for Maven Central that is applied to Maven and Gradle builds in the Analyzer. |

All file paths in the admin configuration are passed to the configuration file provider and are therefore interpreted relative to its root, in the same way as the path of the admin configuration file itself.
Paths that refer to a directory must end with a slash (`/`), because the configuration file providers treat paths without a trailing slash as files when they check whether a path exists.

The following sections describe each part in detail.
A [complete example](#complete-example) is provided at the end of this page.

## Rule Sets

A rule set bundles the configuration files that the Evaluator, and partly the Reporter and the Notifier, use to assess the results of an ORT run.
Each rule set consists of the following properties:

| Property                     | Default                       | Description                                                                                                                    |
| ---------------------------- | ----------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `copyrightGarbageFile`       | `copyright-garbage.yml`       | The path to the [copyright garbage file](https://oss-review-toolkit.org/ort/docs/configuration/copyright-garbage).             |
| `licenseClassificationsFile` | `license-classifications.yml` | The path to the [license classifications file](https://oss-review-toolkit.org/ort/docs/configuration/license-classifications). |
| `resolutionsFile`            | `resolutions.yml`             | The path to the [resolutions file](https://oss-review-toolkit.org/ort/docs/configuration/resolutions).                         |
| `evaluatorRules`             | `evaluator.rules.kts`         | The path to the [evaluator rules script](https://oss-review-toolkit.org/ort/docs/configuration/evaluator-rules).               |

The `defaultRuleSet` section defines the rule set that is used if a run does not specify one.
Properties that are not set fall back to the defaults listed above.

The `ruleSets` section defines additional rule sets under unique names.
Any property that a named rule set omits is inherited from the `defaultRuleSet`, so a named rule set only needs to declare the files that differ:

```hocon
defaultRuleSet {
  copyrightGarbageFile = "curations/copyright-garbage.yml"
  licenseClassificationsFile = "curations/license-classifications.yml"
  resolutionsFile = "curations/resolutions.yml"
  evaluatorRules = "rules/default.rules.kts"
}

ruleSets {
  strict {
    evaluatorRules = "rules/strict.rules.kts"
  }

  webApplication {
    evaluatorRules = "rules/web.rules.kts"
    licenseClassificationsFile = "curations/web-license-classifications.yml"
  }
}
```

Users select a named rule set through the `ruleSet` property of the job configurations when they trigger an ORT run.
If the requested rule set does not exist, the run fails during validation, and the error message lists the available rule set names.
A [parameter validation script](parameter-validation-script.md) can set the rule set on behalf of the user, for instance based on an application category.

## Scanner

The `scanner` section contains settings for the Scanner worker that end users cannot override.

| Property                  | Default                | Description                                                                                                                 |
| ------------------------- | ---------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| `detectedLicenseMappings` | ORT's default mappings | A map from license identifiers reported by a scanner to valid SPDX license expressions.                                     |
| `ignorePatterns`          | ORT's default patterns | A list of glob patterns for file paths whose findings are excluded from scan results.                                       |
| `sourceCodeOrigins`       | `[VCS, ARTIFACT]`      | The origins from which source code is downloaded for scanning, in order of priority. Valid values are `VCS` and `ARTIFACT`. |

The `sourceCodeOrigins` list must not be empty and must not contain duplicates; otherwise, the admin configuration is rejected as invalid.

```hocon
scanner {
  sourceCodeOrigins = [ARTIFACT, VCS]

  ignorePatterns = [
    "**/META-INF/DEPENDENCIES",
    "**/test/**"
  ]

  detectedLicenseMappings {
    "LicenseRef-scancode-generic-cla" = "NOASSERTION"
    "LicenseRef-scancode-free-unknown" = "NOASSERTION"
  }
}
```

:::caution
Setting `ignorePatterns` or `detectedLicenseMappings` replaces ORT's defaults entirely rather than extending them.
If you want to keep the defaults, copy them from ORT's [`ScannerConfiguration`](https://github.com/oss-review-toolkit/ort/blob/main/model/src/main/kotlin/config/ScannerConfiguration.kt) class into your configuration.
:::

## Reporter

The `reporter` section defines which reports users can request and how they are generated.

| Property                   | Default                                 | Description                                                                                                                                                                                                                                                                               |
| -------------------------- | --------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `reports`                  | (none)                                  | A map of report definitions, keyed by report name. See [Report Definitions](#report-definitions).                                                                                                                                                                                         |
| `assets`                   | (none)                                  | A map of named groups of global assets. See [Assets](#assets).                                                                                                                                                                                                                            |
| `howToFixTextProviderFile` | `reporter.how-to-fix-text-provider.kts` | The path to a Kotlin script that provides how-to-fix texts for issues.                                                                                                                                                                                                                    |
| `customLicenseTextDir`     | (none)                                  | The path to a directory with custom license texts, ending with a slash. Each file must be named after the license ID whose text it contains, for example `LicenseRef-example`, without a file extension. If set, these texts are used in addition to the SPDX and ScanCode license texts. |

### Report Definitions

ORT's reporter plugins are technical building blocks that produce a specific output format.
For example, generating a disclosure document in PDF format requires the `PdfTemplate` plugin together with a suitable template.
ORT Server adds a layer on top: in the admin configuration you can define _reports_, each with a meaningful name, that map to a reporter plugin and bring along the assets they need.
Users then request a report by its name, without needing to know which plugin or which templates are involved.

Each entry in `reports` supports the following properties:

| Property               | Required | Description                                                                                                   |
| ---------------------- | -------- | ------------------------------------------------------------------------------------------------------------- |
| `pluginId`             | Yes      | The ID of the ORT reporter plugin that generates this report, for example `PdfTemplate` or `WebApp`.          |
| `description`          | No       | A human-readable description of the report.                                                                   |
| `assetFiles`           | No       | A list of [assets](#assets) referring to single files that must be downloaded before the report is generated. |
| `assetDirectories`     | No       | A list of [assets](#assets) referring to directories whose entire content must be downloaded.                 |
| `assetFilesRefs`       | No       | A list of names of global asset groups whose entries are added to `assetFiles`.                               |
| `assetDirectoriesRefs` | No       | A list of names of global asset groups whose entries are added to `assetDirectories`.                         |
| `nameMapping`          | No       | Overrides the file names of the generated reports. See [Report Names](#report-names).                         |

The set of report formats that users can request is determined as follows:

- Every report definition is available under its name.
- Every reporter plugin that is **not** referenced by any report definition is available under its plugin ID.
- A reporter plugin that **is** referenced by a report definition is no longer available under its own ID.
  This ensures that a plugin that requires specific templates or assets is only used in a properly configured way.

Report names are case-insensitive.
A report definition may only use the name of an existing reporter plugin if it also uses that plugin; for example, a definition named `WebApp` must reference the `WebApp` plugin.

Options for a report are passed through the `config` map of the reporter job configuration.
For a report that is based on a report definition, the Reporter looks up the options as follows:

1. It collects the options stored under the plugin ID, for example `PdfTemplate`, and under the composite key `<pluginId>:<reportName>`, for example `PdfTemplate:disclosure-document`.
   If both are present, they are merged, and the options under the composite key take precedence.
2. Only if neither of these keys is present, it uses the options stored under the report name, for example `disclosure-document`.

Option values that refer to files can use one of two forms, which ORT Server resolves before it invokes the reporter plugin:

- `${workdir}` is replaced with the path of the Reporter's working directory, into which all [assets](#assets) of the requested reports are downloaded.
  Use this form to refer to files that are declared as assets.
- `reporter-template://<path>` causes ORT Server to download the file at `<path>` from the configuration file provider and to replace the value with the path of the downloaded file.

Other values are passed to the plugin unchanged; in particular, plain relative paths are not resolved against the downloaded assets.

When users trigger an ORT run, they can specify options under the report name.
The options are validated against the options of the underlying reporter plugin.
The following example assumes that the templates and fonts are declared as assets as shown in the [Assets](#assets) section:

```json
{
  "reporter": {
    "formats": ["disclosure-document", "vulnerability-report"],
    "config": {
      "disclosure-document": {
        "options": {
          "templatePaths": "${workdir}/disclosure_document.ftl",
          "pdfFontsDir": "${workdir}/fonts"
        }
      },
      "vulnerability-report": {
        "options": {
          "templatePaths": "${workdir}/vulnerability_report.ftl",
          "pdfFontsDir": "${workdir}/fonts"
        }
      }
    }
  }
}
```

The API for triggering runs rejects composite keys such as `PdfTemplate:disclosure-document`, because they do not refer to an installed plugin or a report definition.
Composite keys are therefore intended for a [parameter validation script](parameter-validation-script.md), which can add them to the resolved job configuration.
This is useful to share common options, such as a PDF theme, among all reports based on the same plugin while keeping the templates specific to each report:

```json
{
  "reporter": {
    "formats": ["disclosure-document", "vulnerability-report"],
    "config": {
      "PdfTemplate": {
        "options": {
          "pdfThemeFile": "reporter-template://reporter/pdf-theme.yml",
          "pdfFontsDir": "${workdir}/fonts"
        }
      },
      "PdfTemplate:disclosure-document": {
        "options": { "templatePaths": "${workdir}/disclosure_document.ftl" }
      },
      "PdfTemplate:vulnerability-report": {
        "options": { "templatePaths": "${workdir}/vulnerability_report.ftl" }
      }
    }
  }
}
```

:::caution
Options stored under the report name are ignored as soon as options exist under the plugin ID or the composite key.
Do not combine both styles for the same report.
:::

In the plugin management of ORT Server, a report definition appears as a reporter plugin under its name and can be selected as a default reporter.
It has no availability setting of its own; instead, it inherits the availability of the plugin it uses.
If that plugin is disabled, or restricted for an organization, the same applies to the report definition.
Plugin templates are not applied to report definitions.
To provide default options for a report definition, use a parameter validation script.

### Assets

Templates often reference additional files, such as fonts, images, or stylesheets, by a relative path.
Assets make sure that these files are downloaded from the configuration file provider into the Reporter's working directory before report generation starts.

Each asset supports the following properties:

| Property       | Required | Description                                                                                                                |
| -------------- | -------- | -------------------------------------------------------------------------------------------------------------------------- |
| `sourcePath`   | Yes      | The path of the file or directory in the configuration file provider.                                                      |
| `targetFolder` | No       | The folder, relative to the Reporter's working directory, to place the asset in. Defaults to the working directory itself. |
| `targetName`   | No       | A new name for the downloaded file. Defaults to the last component of `sourcePath`. Only supported for file assets.        |

For directory assets, the `sourcePath` must end with a slash.
The files contained in the directory are downloaded directly into `targetFolder`; no subdirectory named after the source directory is created, and `targetName` is ignored.
Only the files at the top level of the directory are downloaded; subdirectories are not included.

Assets can be declared directly in a report definition, or defined once as a named group in the `assets` section and then referenced from report definitions via `assetFilesRefs` and `assetDirectoriesRefs`.
Users can additionally request global asset groups through the `assetFilesGroups` and `assetDirectoriesGroups` properties of the reporter job configuration.
A reference to an asset group that does not exist causes validation to fail.

```hocon
reporter {
  assets {
    fonts = [
      { sourcePath = "reporter/fonts/", targetFolder = "fonts" }
    ]
    branding = [
      { sourcePath = "reporter/images/logo-2025.png", targetFolder = "images", targetName = "logo.png" }
    ]
  }

  reports {
    disclosure-document {
      pluginId = "PdfTemplate"
      description = "Disclosure document listing all licenses and copyrights in PDF format."
      assetFiles = [
        { sourcePath = "reporter/templates/disclosure_document.ftl" }
      ]
      assetFilesRefs = ["branding"]
      assetDirectoriesRefs = ["fonts"]
    }

    vulnerability-report {
      pluginId = "PdfTemplate"
      description = "Report listing all known vulnerabilities in PDF format."
      assetFiles = [
        { sourcePath = "reporter/templates/vulnerability_report.ftl" }
      ]
      assetFilesRefs = ["branding"]
      assetDirectoriesRefs = ["fonts"]
    }
  }
}
```

### Report Names

By default, the reporter plugin determines the file names of the reports it produces.
The optional `nameMapping` of a report definition replaces these names with a custom naming scheme:

| Property            | Default | Description                                                                                                   |
| ------------------- | ------- | ------------------------------------------------------------------------------------------------------------- |
| `namePrefix`        | —       | The base name for all generated files. The original file extensions are kept.                                 |
| `startIndex`        | `1`     | The first index used when the report produces more than one file, for example `report-1.pdf`, `report-2.pdf`. |
| `alwaysAppendIndex` | `false` | If `true`, the index is appended even if the report produces only a single file.                              |

```hocon
reporter {
  reports {
    disclosure-document {
      pluginId = "PdfTemplate"
      nameMapping {
        namePrefix = "disclosure-document"
      }
    }
  }
}
```

## Notifier

The `notifier` section configures the Notifier worker, which can send emails or create Jira issues once an ORT run has finished.

| Property                   | Default                 | Description                                                                         |
| -------------------------- | ----------------------- | ----------------------------------------------------------------------------------- |
| `notifierRules`            | `run.notifications.kts` | The path to the ORT notifier script to execute.                                     |
| `mail`                     | (none)                  | The mail server used to send emails. If omitted, no emails are sent.                |
| `jira`                     | (none)                  | The Jira server used to create issues. If omitted, no Jira issues are created.      |
| `disableMailNotifications` | `false`                 | Temporarily disables email notifications without removing the `mail` configuration. |
| `disableJiraNotifications` | `false`                 | Temporarily disables Jira notifications without removing the `jira` configuration.  |

The `mail` section supports the following properties:

| Property      | Default     | Description                                                |
| ------------- | ----------- | ---------------------------------------------------------- |
| `host`        | `localhost` | The host name of the SMTP server.                          |
| `port`        | `587`       | The port of the SMTP server.                               |
| `username`    | (required)  | The **name of the secret** holding the SMTP user name.     |
| `password`    | (required)  | The **name of the secret** holding the SMTP password.      |
| `ssl`         | `true`      | Whether to use SSL/TLS when connecting to the SMTP server. |
| `fromAddress` | (required)  | The sender address of outgoing emails.                     |

The `jira` section supports the following properties:

| Property   | Description                                                         |
| ---------- | ------------------------------------------------------------------- |
| `url`      | The URL of the Jira server, for example `https://jira.example.com`. |
| `username` | The **name of the secret** holding the Jira user name.              |
| `password` | The **name of the secret** holding the Jira password or API token.  |

:::important
The `username` and `password` properties do not contain the credentials themselves.
They contain the names of configuration secrets that ORT Server resolves through its configuration secret provider, as described in [Sensitive Configuration Options](config.md#sensitive-configuration-options).
These secrets are managed by the administrators of ORT Server; secrets that users create for their organizations, products, or repositories are not available here.
This keeps credentials out of the admin configuration file, which is typically stored in a version control system.
:::

```hocon
notifier {
  notifierRules = "notifier/run.notifications.kts"

  mail {
    host = "smtp.example.com"
    port = 587
    username = "notifier.mail.username"
    password = "notifier.mail.password"
    ssl = true
    fromAddress = "ort-server@example.com"
  }

  jira {
    url = "https://jira.example.com"
    username = "notifier.jira.username"
    password = "notifier.jira.password"
  }
}
```

## Maven Central Mirror

The optional `mavenCentralMirror` section defines a mirror that replaces Maven Central for all projects analyzed by ORT Server.
This is useful in environments where direct access to Maven Central is restricted or where a caching proxy is in place.
If the section is present, the Analyzer generates the corresponding Maven `settings.xml` and a Gradle init script before it analyzes a project.

| Property   | Required | Description                                                                                                                                                        |
| ---------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `id`       | Yes      | A unique identifier for the mirror.                                                                                                                                |
| `name`     | Yes      | A human-readable name for the mirror.                                                                                                                              |
| `url`      | Yes      | The URL of the mirror.                                                                                                                                             |
| `mirrorOf` | Yes      | The repositories the mirror applies to, using Maven's [`mirrorOf` syntax](https://maven.apache.org/guides/mini/guide-mirror-settings.html), for example `central`. |
| `username` | No       | The **name of the secret** holding the user name for the mirror.                                                                                                   |
| `password` | No       | The **name of the secret** holding the password for the mirror.                                                                                                    |

As for the notifier, `username` and `password` refer to [configuration secrets](config.md#sensitive-configuration-options), not to the credentials themselves.
Credentials are only used if both properties are set.
If the environment configuration of a repository declares its own Maven mirror with the same `mirrorOf` value, that mirror takes precedence over the global mirror in the generated Maven settings.

```hocon
mavenCentralMirror {
  id = "central-mirror"
  name = "Company Maven Central Mirror"
  url = "https://repo.example.com/maven-central"
  mirrorOf = "central"
  username = "maven.mirror.username"
  password = "maven.mirror.password"
}
```

## Validation

ORT Server validates the admin configuration when an ORT run is started.
The validation takes place in the Config worker, before any other worker begins processing, and checks the following:

- The structure of the file is valid HOCON and matches the model described on this page.
- The `scanner.sourceCodeOrigins` list is neither empty nor contains duplicates.
- All reporter plugins referenced by report definitions exist, and no report definition reuses the name of a different reporter plugin.
- All references to global asset groups can be resolved.
- All rule set files and reporter configuration files that are referenced with a non-default path, as well as all reporter assets, exist in the configuration file provider.
  The notifier script configured in `notifier.notifierRules` is not part of this check; a missing script is only detected when the Notifier runs at the end of the ORT run.
- The rule set, report formats, and asset groups requested in the job configurations of the run are defined in the admin configuration.

If any check fails, the run fails, and the issues found are attached to it so that they are visible to users in the UI and through the API.

:::tip
Because every ORT run validates the admin configuration, a faulty change affects all new runs immediately.
If your configuration file provider supports versioning, test changes on a separate branch first and trigger a run with the corresponding configuration context before promoting the change.
:::

## Complete Example

The following example combines all sections described above:

```hocon
defaultRuleSet {
  copyrightGarbageFile = "curations/copyright-garbage.yml"
  licenseClassificationsFile = "curations/license-classifications.yml"
  resolutionsFile = "curations/resolutions.yml"
  evaluatorRules = "rules/default.rules.kts"
}

ruleSets {
  strict {
    evaluatorRules = "rules/strict.rules.kts"
  }
}

scanner {
  sourceCodeOrigins = [ARTIFACT, VCS]
}

reporter {
  howToFixTextProviderFile = "reporter/how-to-fix-text-provider.kts"
  customLicenseTextDir = "reporter/custom-license-texts/"

  assets {
    fonts = [
      { sourcePath = "reporter/fonts/", targetFolder = "fonts" }
    ]
  }

  reports {
    disclosure-document {
      pluginId = "PdfTemplate"
      description = "Disclosure document in PDF format."
      assetFiles = [
        { sourcePath = "reporter/templates/disclosure_document.ftl" }
      ]
      assetDirectoriesRefs = ["fonts"]
      nameMapping {
        namePrefix = "disclosure-document"
      }
    }

    web-app {
      pluginId = "WebApp"
      description = "Interactive HTML report."
    }
  }
}

notifier {
  notifierRules = "notifier/run.notifications.kts"

  mail {
    host = "smtp.example.com"
    username = "notifier.mail.username"
    password = "notifier.mail.password"
    fromAddress = "ort-server@example.com"
  }
}

mavenCentralMirror {
  id = "central-mirror"
  name = "Company Maven Central Mirror"
  url = "https://repo.example.com/maven-central"
  mirrorOf = "central"
}
```
