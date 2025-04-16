# ORT Server Client CLI (`osc`)

The ORT Server Client CLI (`osc`) is a command-line interface designed to interact with the ORT Server. It provides essential functionality for managing ORT runs and retrieving results.

## Installation

The latest version of `osc` can be obtained from the [GitHub releases page](https://github.com/eclipse-apoapsis/ort-server/releases/latest).

### macOS

When using the CLI on macOS, Apple's Gatekeeper restrictions may prevent the application from running.
To bypass this, the quarantine attribute from the `osc` binary needs to be removed.
This can be done by running the following command:

```shell
xattr -d com.apple.quarantine /path/to/osc
```

## Usage in GitHub Actions

When using `osc` in a GitHub action, the [`setup-osc`](https://github.com/eclipse-apoapsis/setup-osc) action can be used to install `osc` and authenticate with an ORT Server instance.

The following example demonstrates how to integrate `osc` in a GitHub Actions workflow to start an ORT run, retrieve the created reports, and store them as workflow artifacts:

```yaml
jobs:
  run-osc:
    runs-on: ubuntu-latest
    steps:
      - name: Setup OSC
        uses: eclipse-apoapsis/setup-osc@main
        with:
          osc-version: 0.1.0-RC16
          url: https://ort-server.example.com
          username: user
          password: ${{ secrets.ORT_SERVER_PASSWORD }}

      - name: Start ORT run
        run: |
          osc runs start --parameters '{
            "revision": "${{ github.head_ref || github.ref_name }}",
            "jobConfigs": {
              "analyzer": {},
              "advisor": {},
              "evaluator": {},
              "scanner": {},
              "reporter": {},
              "notifier": {}
            }
          }'
        env:
          OSC_REPOSITORY_ID: 42
          OSC_RUNS_START_WAIT: true # Block the runner until the ORT Server run is finished.

      - name: Download Reports
        run: |
          osc runs download reports
        env:
          OSC_DOWNLOAD_REPORTS_FILE_NAMES: 'scan-report-web-app.html'
          OSC_DOWNLOAD_REPORTS_OUTPUT_DIR: './reports'

      - name: Upload reports
        id: upload
        uses: actions/upload-artifact@v4
        with:
          name: reports
          path: reports/
```

All available settings for `setup-osc` can be found [in the Repository of the action](https://github.com/eclipse-apoapsis/setup-osc).

## Usage

### Authentication

Most operations require authentication with your ORT Server instance. The following command-line arguments are used for authentication:

```shell
osc auth login \
    --url <ORT_SERVER_URL> \
    --username <USERNAME> \
    --password <PASSWORD>
```

### Basic Operations

#### Starting an ORT Run

A basic ORT run can be started using:

```shell
osc runs start --repository-id <REPOSITORY_ID> --parameters '{
  "jobConfigs": {},
  "revision": "main"
}'
```

#### Monitoring Run Status

To check the status of the previously started run:

```shell
osc runs info
```

#### Downloading Reports

To retrieve reports from a completed ORT run, execute:

```shell
osc runs download reports --file-names scan-report-web-app.html --output-dir /tmp
```

### Environment Variables

For automated environments like CI/CD pipelines, all command-line arguments can be configured using environment variables.  
For example, to authenticate:

```shell
export OSC_ORT_SERVER_URL="<ORT_SERVER_URL>"
export OSC_ORT_SERVER_USERNAME="<USERNAME>"
export OSC_ORT_SERVER_PASSWORD="<PASSWORD>"

osc auth login
```

## Command Documentation

For detailed information about available commands and their options:

```shell
osc --help
```

Each subcommand also provides specific help documentation when invoked with the `--help` flag.
