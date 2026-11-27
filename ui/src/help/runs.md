# Runs

A run analyzes one revision of a repository with the OSS Review Toolkit (ORT). It identifies the
dependencies of the projects in the repository, looks up known vulnerabilities, scans the source
code for licenses and copyrights, checks the results against the policies of your organization,
and creates reports.

In most cases, you can keep the default settings. The defaults are provided by the administrators
of this ORT Server instance, and a repository can add its own configuration in its `.ort.yml` and
`.ort.env.yml` files. Change a setting only if this run needs to differ.

[Read more about the jobs of a run in the ORT documentation](https://oss-review-toolkit.org/ort/docs/getting-started/tutorial)

## Configuration Context {#job-config-context}

The version of the server configuration to use for this run, such as a branch name. The server
configuration includes, for example, the evaluator rules, license classifications and report
templates. Which values are valid depends on how the administrators have set up this instance; ask
them if you are unsure. If the specified version cannot be found, the run fails.

Leave this field empty to use the default configuration. A configuration context is mainly used by
administrators to test configuration changes, such as a new rule, before making them the default.

## Environment Configuration Path {#environment-config-path}

The path to the environment configuration file in the repository. Specify it only if the file is
not located at the default path `.ort.env.yml` in the repository root.

The environment configuration describes what is required to build the repository, such as the
infrastructure services and credentials for private artifact repositories, environment variables,
and package manager settings.

## Rule Set {#rule-set}

The name of the rule set to check the results of this run against. A rule set contains the
evaluator rules, license classifications, resolutions and copyright statements to ignore.

Rule sets are defined by the administrators of this ORT Server instance. Leave this field empty to
use the default rule set. If you specify a rule set that does not exist, the run fails, and the
error message lists the available rule sets.

## Configuration Parameters {#parameters}

Key-value pairs that adjust the settings of the run. Parameters provide a simpler way to configure
a run, for example by selecting an application category instead of a specific rule set.

The available parameters are specific to this ORT Server instance. Ask your administrators which
parameters you can use.

## ORT Labels {#labels}

Key-value pairs that are stored with the results of this run. Labels have no effect on their own,
but rules can use them. For example, a rule could apply stricter checks to runs with the label
`distribution=external`.

## Includes and Excludes {#includes-excludes}

Patterns that mark parts of the analyzed projects as included or excluded, in addition to those in
the `.ort.yml` file of the repository.

- **Paths** match files and directories relative to the repository root, such as `docs/**` or
  `**/test/**`. Excluding a path also excludes the projects defined in it.
- **Scopes** match dependency scopes using a regular expression, such as `test.*` for test
  dependencies.

Excluded parts are listed separately in reports and are usually ignored by rules. Jobs can also be
configured to skip them. If you add an include, everything of the same kind that the include does
not match is treated as excluded. Each entry requires a reason, which is shown in the reports.

## Jobs {#job-configs}

A run consists of several jobs. The Analyzer always runs; you can enable, disable and configure
the other jobs below. The Advisor and the Scanner start when the Analyzer has finished, the
Evaluator then checks their results, and the Reporter and the Notifier run last. The Reporter also
runs if a previous job fails, so that you receive reports about the problem.

### Analyzer {#analyzer}

Identifies the projects in the repository and determines their dependencies using package managers
such as Maven, npm or Gradle. All other jobs build on its results.

### Advisor {#advisor}

Looks up known vulnerabilities and defects of the dependencies in sources such as OSV or
VulnerableCode.

### Scanner {#scanner}

Scans the source code of the projects and their dependencies for licenses and copyright
statements. Results from earlier scans of the same source code are reused.

### Evaluator {#evaluator}

Checks the results against the rules of the selected rule set and reports rule violations, for
example a dependency whose license your organization does not allow.

### Reporter {#reporter}

Creates reports in the selected formats, such as an SBOM or a web application report.

### Notifier {#notifier}

Sends a notification when the run has finished, for example an email to the specified recipients.
