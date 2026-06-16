# Elasticsearch Log Access Implementation

This module provides an implementation of the [Log access abstraction](../README.md) based on Elasticsearch.

## Synopsis

The `ElasticsearchLogFileProvider` sends requests against the Elasticsearch Search API to retrieve the logs of a
specific ORT run and component. Results are fetched in chronological order and paginated using Elasticsearch's
`search_after` mechanism with a stable sort on `timestamp` and `sequenceNumber`.

The provider assumes a canonical indexed schema that ORT Server can query independent of the concrete collector used
to ship logs to Elasticsearch. Deployments are responsible for creating compatible Elasticsearch mappings and for
normalizing metadata and extracted log fields into this shape before documents are indexed.

The provider expects log documents to follow the structured JSON schema produced by the
[`OrtServerJsonEncoder`](../../utils/logging/src/main/kotlin/OrtServerJsonEncoder.kt) (the same field names emitted by
the Logback JSON encoder, such as `formattedMessage`, `level`, `timestamp`, `sequenceNumber`, and the MDC entries under
`mdc.*`). The relevant fields are:

| Field              | Used as                                | Expected Elasticsearch type    | Purpose                                                                                                                                         |
|--------------------|----------------------------------------|--------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| `namespace`        | filter                                 | `keyword`                      | Exact-match deployment namespace filter; must match `elasticsearchNamespace`. The field name is configurable via `elasticsearchNamespaceField`. |
| `mdc.component`    | filter (`mdc.component.keyword`)       | `text` with `keyword` subfield | Exact-match ORT component filter using ORT Server component names.                                                                              |
| `mdc.ortRunId`     | filter (`mdc.ortRunId.keyword`)        | `text` with `keyword` subfield | Exact-match ORT run ID filter.                                                                                                                  |
| `level`            | filter (`level.keyword`) and `_source` | `text` with `keyword` subfield | Exact-match log level filter; the level value is also prepended to each written log line.                                                       |
| `timestamp`        | range filter, primary sort, `_source`  | `date` (`epoch_millis`)        | Log event timestamp; used for range queries, primary sorting, and rendered as the leading timestamp of each log line.                           |
| `sequenceNumber`   | secondary sort                         | `long`                         | Stable tie-breaker for `search_after` pagination among hits that share the same `timestamp`.                                                    |
| `formattedMessage` | `_source`                              | `text` recommended             | Rendered log line written to the downloaded log file.                                                                                           |
| `throwable`        | `_source`                              | `text` recommended             | Optional rendered throwable; appended after the message when present.                                                                           |

Filtering and sorting use the indexed (`keyword`) fields, while the log content is read from `_source`. The provider
therefore requests only `level`, `formattedMessage`, `throwable`, and `timestamp` in `_source` and uses the indexed
fields for the remaining filters and sorts. Each downloaded log line is rendered as `<timestamp> <level> <message>`,
with the `throwable` (if present) appended on the following line.

The `formattedMessage` field is written to the downloaded log file, one line per hit. It does not need to be indexed
for search by the provider, but it must be present in `_source`. Indexing `formattedMessage` as a `text` field is
recommended for Kibana, so users can search log lines, exceptions, request paths, and other free-form text. A
`.keyword` subfield can be useful for exact matches, sorting, or aggregations, but should usually have an
`ignore_above` limit to avoid indexing very large log lines as keyword terms.

Although `mdc.ortRunId` contains numeric values, the provider treats it as an identifier and queries its `keyword`
subfield. This matches Elasticsearch's guidance for numeric-looking identifiers that are primarily used in term
queries.

### Field prefix

If the indexing pipeline nests all log-line fields under a common prefix (for example, when Logstash is configured to
add a custom prefix to all fields during indexing), set `elasticsearchFieldPrefix`. The provider then prepends this
prefix to every field taken from the log line. The namespace field is *not* prefixed, because it is derived
from deployment metadata rather than from the log line. Prefixed fields are returned by Elasticsearch as nested
objects and resolved accordingly when reading values from `_source`.

## Configuration

Configuration is read from the `logFileService` section.

```hocon
logFileService {
  name = "elasticsearch"
  elasticsearchServerUrl = "https://elasticsearch.example.org"
  elasticsearchIndex = "ort-server-logs-*"
  elasticsearchNamespace = "prod"
  elasticsearchNamespaceField = "namespace"
  elasticsearchFieldPrefix = "ortserver"
  elasticsearchPageSize = 1000
  elasticsearchApiKey = "base64-api-key"
}
```

Supported properties:

| Property                      | Variable                        | Description                                                                               | Default     | Secret |
|-------------------------------|---------------------------------|-------------------------------------------------------------------------------------------|-------------|--------|
| `elasticsearchServerUrl`      | `ELASTICSEARCH_SERVER_URL`      | Base URL of the Elasticsearch instance.                                                   | mandatory   | no     |
| `elasticsearchIndex`          | `ELASTICSEARCH_INDEX`           | Index or index pattern to query.                                                          | mandatory   | no     |
| `elasticsearchNamespace`      | `ELASTICSEARCH_NAMESPACE`       | Namespace label used to restrict queries.                                                 | mandatory   | no     |
| `elasticsearchNamespaceField` | `ELASTICSEARCH_NAMESPACE_FIELD` | Name of the field that holds the namespace value used by the namespace filter.            | `namespace` | no     |
| `elasticsearchFieldPrefix`    | `ELASTICSEARCH_FIELD_PREFIX`    | Optional prefix prepended to all log-line fields (everything except the namespace field). | undefined   | no     |
| `elasticsearchPageSize`       | `ELASTICSEARCH_PAGE_SIZE`       | Number of hits to fetch per search request.                                               | `1000`      | no     |
| `elasticsearchUsername`       | `ELASTICSEARCH_USERNAME`        | Optional username for Basic Auth. Ignored when an API key is configured.                  | undefined   | no     |
| `elasticsearchPassword`       | `ELASTICSEARCH_PASSWORD`        | Optional password for Basic Auth.                                                         | undefined   | yes    |
| `elasticsearchApiKey`         | `ELASTICSEARCH_API_KEY`         | Optional Elasticsearch API key. Takes precedence over Basic Auth.                         | undefined   | yes    |

If both Basic Auth credentials and an API key are configured, the API key is used and Basic Auth is ignored.

In addition, default properties of the HTTP client that is used to send requests to the Elasticsearch API can be configured in an `elasticsearchHttpClient` section.

## Query Behavior

Queries use a bool filter with:

- namespace term filter on the configured namespace field (default `namespace`)
- component term filter on `mdc.component.keyword`
- ORT run ID term filter on `mdc.ortRunId.keyword`
- log level terms filter on `level.keyword`
- time range filter on `timestamp`

Results are sorted ascending by `timestamp` and `sequenceNumber` and fetched via Elasticsearch's `search_after`
mechanism until all matching hits have been retrieved. Instead of using offset-based paging, `search_after` asks
Elasticsearch for the next page after the sort values of the last hit from the previous page. This avoids deep paging
limits.

The secondary `sequenceNumber` field is required because multiple log lines can share the same `timestamp`. It must be
present on every document and provide a stable, monotonically increasing tie-breaker for log lines with the same
timestamp. ORT Server's logging stack emits this value automatically; deployments only need to ensure it is indexed in
a sortable numeric type such as `long`.

## Collector Normalization

ORT Server queries a fixed set of field names derived from its structured log schema. The only per-deployment
customizations are the namespace field name (`elasticsearchNamespaceField`) and an optional global field prefix
(`elasticsearchFieldPrefix`); the remaining field names are fixed. Collector pipelines must therefore normalize
platform-specific metadata and log content into this schema before documents are indexed.

For Kubernetes deployments, this typically means deriving `namespace` from pod metadata, and ensuring that JSON logging
is enabled by setting the `LOG_FORMAT` environment variable to `json` in all ORT Server containers.

If the filter or sort fields are missing or mapped incompatibly, ORT Server queries will not find the expected log
documents. Hits without `_source.formattedMessage` are skipped because there is no rendered log line to write to the
downloaded file.

An Elasticsearch index template for the required fields can look like this. The `.keyword` subfields on
`mdc.component`, `mdc.ortRunId`, and `level` are what the provider filters and sorts on; they correspond to
Elasticsearch's default dynamic mapping for string fields.

```json
{
  "mappings": {
    "properties": {
      "namespace": { "type": "keyword" },
      "mdc": {
        "properties": {
          "component": {
            "type": "text",
            "fields": {
              "keyword": { "type": "keyword", "ignore_above": 256 }
            }
          },
          "ortRunId": {
            "type": "text",
            "fields": {
              "keyword": { "type": "keyword", "ignore_above": 256 }
            }
          }
        }
      },
      "level": {
        "type": "text",
        "fields": {
          "keyword": { "type": "keyword", "ignore_above": 256 }
        }
      },
      "timestamp": {
        "type": "date",
        "format": "strict_date_optional_time||epoch_millis"
      },
      "sequenceNumber": { "type": "long" },
      "formattedMessage": {
        "type": "text",
        "fields": {
          "keyword": {
            "type": "keyword",
            "ignore_above": 8191
          }
        }
      },
      "throwable": { "type": "text" }
    }
  }
}
```
