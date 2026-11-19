# Runs

A run analyzes one revision of a repository with the OSS Review Toolkit (ORT). It finds the
dependencies of the projects in the repository, looks up their vulnerabilities, scans their source
code for licenses and copyrights, checks the results against the rules of your organization, and
produces reports.

Most settings of a run can stay as they are: the defaults come from the configuration of this ORT
Server instance, and the repository can add its own configuration in `.ort.yml` and
`.ort.env.yml`. Set something here only when this run needs to differ.

[Read more about the jobs of a run in the ORT documentation](https://oss-review-toolkit.org/ort/docs/getting-started/tutorial)

## Configuration Context {#job-config-context}

The version of the server configuration to use for this run. ORT Server reads its configuration
files, such as evaluator rules, license classifications and report templates, from a
configuration provider, and the context tells the provider which version to read. What the context
means depends on the provider: for a provider that reads from a Git repository, such as the
`GitHubConfigFileProvider`, it is the Git revision to check out: a branch, a tag or a commit.

The context is resolved once when the run starts, so all jobs of the run see the same
configuration even if it changes while the run is in progress.

Leave it empty to use the default configuration. Administrators use the context to try out a
change to the configuration, for example a new rule, on a branch before making it the default.

## Environment Configuration Path {#environment-config-path}

The path in the repository of the environment configuration file, if it is not at the default
location `.ort.env.yml` in the repository root.

The environment configuration declares what the build of the repository needs: the infrastructure
services, such as private artifact repositories, with the credentials to access them, the
environment variables for the build, and the settings that package managers need to find their
repositories.

## Rule Set {#rule-set}

The name of the rule set to check the results of this run against. A rule set bundles the files
that the Evaluator, and partly the Reporter and the Notifier, use: the evaluator rules, the license
classifications, the resolutions, and the list of copyright statements to ignore.

The administrators of this ORT Server instance define the rule sets. Leave the field empty to use
the default rule set. A run that names a rule set that does not exist fails, and its error message
lists the available names.

## Configuration Parameters {#parameters}

Key-value pairs passed to the validation script of this ORT Server instance. The script runs
before the Analyzer and turns the parameters into the settings of the jobs, so that you can
choose, for example, an application category instead of a technical rule set, or a scanner by a
short name instead of its address and credentials.

Which parameters exist and what they do depends entirely on the script, so ask the
administrators of this instance.

## ORT Labels {#labels}

Key-value pairs stored as labels in the ORT result of this run. ORT does not interpret labels
itself; custom configuration does, most often the evaluator rules. A rule could, for example,
apply stricter checks to runs labelled `distribution=external`.

## Includes and Excludes {#includes-excludes}

Patterns that mark parts of the analyzed projects as included or excluded, in addition to the
includes and excludes in the repository's `.ort.yml`.

- **Paths** match files and directories, relative to the repository root, such as `docs/**` or
  `**/test/**`. Excluding a path excludes the projects defined in it.
- **Scopes** match the dependency scopes of the package managers with a regular expression, such
  as `test.*` for test dependencies.

Excluded parts are marked as excluded in the results: reports show them separately, evaluator
rules usually ignore them, and the jobs can skip them with their Skip excluded settings. An
include turns this around: once there is an include of a kind, everything of that kind that it
does not match counts as excluded. Each entry needs a reason, which reports show next to the
excluded parts.

## Jobs {#job-configs}

A run consists of jobs, each done by its own worker. The Analyzer always runs; switch the others
on or off and configure them below. The Advisor and the Scanner start when the Analyzer is done,
the Evaluator works on their results, and the Reporter and the Notifier come last. The Reporter
also runs after a failed job, so that you get reports about what went wrong.

### Analyzer {#analyzer}

Finds the projects in the repository and resolves their dependencies with the package managers of
the projects, such as Maven, npm or Gradle. Its result is the dependency tree of every project,
which all other jobs build on.

### Advisor {#advisor}

Looks up known vulnerabilities and defects of the dependencies in services such as OSV or
VulnerableCode.

### Scanner {#scanner}

Downloads the source code of the projects and packages and scans it for licenses and copyright
statements. Results of earlier scans of the same source code are reused.

### Evaluator {#evaluator}

Checks the results against the rules of the selected rule set, and reports rule violations, for
example a dependency under a license that your organization does not allow.

### Reporter {#reporter}

Creates the reports of the run in the selected formats, such as an SBOM or a web application
report.

### Notifier {#notifier}

Sends a notification about the finished run, such as an email to the recipient addresses.
