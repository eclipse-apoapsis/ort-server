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

## Usage

### Authentication

Most operations require authentication with your ORT Server instance. The following command-line arguments are used for authentication:

```shell
osc auth login \
    --base-url <ORT_SERVER_URL> \
    --token-url <TOKEN_URL> \
    --client-id <CLIENT_ID> \
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
export OSC_ORT_SERVER_BASE_URL="<ORT_SERVER_URL>"
export OSC_ORT_SERVER_TOKEN_URL="<TOKEN_URL>"
export OSC_ORT_SERVER_CLIENT_ID="<CLIENT_ID>"
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
