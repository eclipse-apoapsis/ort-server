# Grafana Loki Log Access Implementation

This module provides an implementation of the [Log access abstraction](../README.md) based on [Grafana Loki](https://grafana.com/oss/loki/).

## Synopsis

The [LokiLogFileProvider](src/main/kotlin/LokiLogFileProvider.kt) class provided by this module sends requests against the [HTTP API](https://grafana.com/docs/loki/latest/reference/api/) of a configured Grafana Loki instance to retrieve the logs of a specific ORT run.

In order to obtain the logs of a specific ORT run step, the provider sends a single [LogQL](https://grafana.com/docs/loki/latest/query/) query to the server which selects the ORT run by its ID, a configurable (Kubernetes) namespace, and the log source - which corresponds to the worker responsible for this step.
Typically, it is not possible to fetch all log statements in a single call.
The responses from Loki are rather verbose and therefore consume a certain amount of memory.
To prevent issues with excessive memory consumption, the Loki API always applies a limit to query results.
The limit is set to 100 (log lines) per default, but can be overridden by a parameter.
This module allows configuring this limit.
The `LokiLogFileProvider` class passes the configured limit to the Loki API and evaluates the result size to determine whether more logs need to be fetched: if the number of returned log lines is greater than or equal to the specified limit, the provider fetches another chunk of data; otherwise, the log is considered complete.

According to the documentation about [Authentication](https://grafana.com/docs/loki/latest/operations/authentication/), Grafana Loki does not support authentication mechanisms by itself.
Instead, the system can be secured via a reverse proxy which can offer different authentication schemes.
This module currently implements support for an optional Basic Authentication: If username and password are specified in the configuration, the requests sent against the Loki API contain a corresponding `Authorization` header for Basic Authentication.

Loki supports a [multi-tenancy](https://grafana.com/docs/loki/latest/operations/multi-tenancy/) mode.
When using this mode, every request must contain a special header that determines the current organization ID.
The configuration of this module supports such a property: if it is defined, the header is added automatically.

## Configuration

As defined by the Log Access SPI module, the configuration takes place in a section named `logFileProvider`.
Here a number of properties specific to this module can be set as shown in the listing below.
Mandatory properties are the server URL and the namespace; the other properties are optional.

```
logFileProvider {
  name = "loki"
  lokiServerUrl = https://loki.example.org/
  lokiNamespace = prod
  lokiQueryLimit = 1500
  lokiUsername = scott
  lokiPassword = tiger
  lokiTenantId = 42
}
```

This table contains a description of the supported configuration properties:

| Property       | Variable           | Description                                                                                                                                                                                                      | Default   | Secret |
|----------------|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|--------|
| lokiServerUrl  | LOKI\_SERVER\_URL  | The URL under which the Loki HTTP API can be reached. This is just the base URL; the path for the endpoint (including `/loki/api/v1`) is appended automatically.                                                 | mandatory | no     |
| lokiNamespace  | LOKI\_NAMESPACE    | The name of the namespace in Kubernetes in which the worker pods are running. The namespace is added to the query sent to the Loki API to reduce the amount of data to search for.                               | mandatory | no     |
| lokiQueryLimit | LOKI\_QUERY\_LIMIT | The value to be passed as `limit` parameter to the Loki query API. It determines the number of log lines that can be retrieved in a single call. If more logs are available, the provider sends another request. | 1000      | no     |
| lokiUserName   | LOKI\_USER\_NAME   | An optional username for Basic Auth authentication.                                                                                                                                                              | undefined | no     |
| lokiPassword   | LOKI\_PASSWORD     | An optional password for Basic Auth authentication. If credentials are defined, the provider implementation adds an `Authorization` header for Basic Auth to requests to the query API.                          | undefined | yes    |
| lokiTenantId   | LOKI\_TENANT\_ID   | The ID of the tenant if Loki is running in multi-tenancy mode.                                                                                                                                                   | undefined | no     |
