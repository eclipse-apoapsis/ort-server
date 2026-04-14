# Elasticsearch Log Access Implementation

This module provides an implementation of the [Log access abstraction](../README.md) based on Elasticsearch.

## Synopsis

The `ElasticsearchLogFileProvider` sends requests against the Elasticsearch Search API to retrieve the logs of a
specific ORT run and component. Results are fetched in chronological order and paginated using Elasticsearch's
`search_after` mechanism with a stable sort on `time` and `sortId`.

The provider assumes a canonical indexed schema that ORT Server can query independent of the concrete collector used
to ship logs to Elasticsearch. Deployments are responsible for creating compatible Elasticsearch mappings and for
normalizing metadata and extracted log fields into this shape before documents are indexed.

The provider expects log documents to contain these fields:

| Field | Expected Elasticsearch type | Purpose |
|-------|-----------------------------|---------|
| `namespace` | `keyword` | Exact-match deployment namespace filter; must match `elasticsearchNamespace`. |
| `component` | `keyword` | Exact-match ORT component filter using ORT Server component names. |
| `ortRunId` | `keyword` with optional `long` subfield, for example `ortRunId.numeric` | Exact-match ORT run ID filter. |
| `level` | `keyword` | Exact-match log level filter using ORT Server log level names. |
| `time` | `date` | ORT log event timestamp used for range queries and primary sorting. |
| `sortId` | `keyword` | Secondary sort key for stable `search_after` pagination. |
| `message` | present in `_source`; `text` recommended for Kibana searches | Rendered log line written to the downloaded log file. |

The `message` field is written to the downloaded log file unchanged, one line per hit. It does not need to be indexed
for search by the provider, but it must be present in `_source`. Indexing `message` as a `text` field is recommended for
Kibana, so users can search log lines, exceptions, request paths, and other free-form text. A `.keyword` subfield can be
useful for exact matches, sorting, or aggregations, but should usually have an `ignore_above` limit to avoid indexing
very large log lines as keyword terms.

Although `ortRunId` contains numeric values, the provider treats it as an identifier and queries it as a keyword. This
matches Elasticsearch's guidance for numeric-looking identifiers that are primarily used in term queries. A numeric
multi-field can still be added for future range queries or numeric sorting without changing the provider's exact-match
query behavior.

## Configuration

Configuration is read from the `logFileService` section.

```hocon
logFileService {
  name = "elasticsearch"
  elasticsearchServerUrl = "https://elasticsearch.example.org"
  elasticsearchIndex = "ort-server-logs-*"
  elasticsearchNamespace = "prod"
  elasticsearchPageSize = 1000
  elasticsearchApiKey = "base64-api-key"
  elasticsearchTimeoutSec = 30
}
```

Supported properties:

| Property | Variable | Description | Default | Secret |
|----------|----------|-------------|---------|--------|
| `elasticsearchServerUrl` | `ELASTICSEARCH_SERVER_URL` | Base URL of the Elasticsearch instance. | mandatory | no |
| `elasticsearchIndex` | `ELASTICSEARCH_INDEX` | Index or index pattern to query. | mandatory | no |
| `elasticsearchNamespace` | `ELASTICSEARCH_NAMESPACE` | Namespace label used to restrict queries. | mandatory | no |
| `elasticsearchPageSize` | `ELASTICSEARCH_PAGE_SIZE` | Number of hits to fetch per search request. | `1000` | no |
| `elasticsearchUsername` | `ELASTICSEARCH_USERNAME` | Optional username for Basic Auth. Ignored when an API key is configured. | undefined | no |
| `elasticsearchPassword` | `ELASTICSEARCH_PASSWORD` | Optional password for Basic Auth. | undefined | yes |
| `elasticsearchApiKey` | `ELASTICSEARCH_API_KEY` | Optional Elasticsearch API key. Takes precedence over Basic Auth. | undefined | yes |
| `elasticsearchTimeoutSec` | `ELASTICSEARCH_TIMEOUT_SEC` | Optional request timeout in seconds. | `30` | no |

If both Basic Auth credentials and an API key are configured, the API key is used and Basic Auth is ignored.

## Query Behavior

Queries use a bool filter with:

- namespace term filter on `namespace`
- component term filter on `component`
- ORT run ID term filter on `ortRunId`
- log level terms filter on `level`
- time range filter on `time`

Results are sorted ascending by `time` and `sortId` and fetched via Elasticsearch's `search_after` mechanism until all
matching hits have been retrieved. Instead of using offset-based paging, `search_after` asks Elasticsearch for the next
page after the sort values of the last hit from the previous page. This avoids deep paging limits.

The secondary `sortId` field is required because multiple log lines can share the same `time` value. It should be a
keyword-compatible value that is stable for the indexed log document and unique enough to distinguish log lines with the
same timestamp. The value does not need to be meaningful to users.

Good sources for `sortId`, in order of preference, are:

- a collector-provided event ID, sequence number, or log-file offset if available
- a deterministic hash of stable event identity fields such as pod UID, container ID, stream, log-file offset,
  high-precision event timestamp, and, if needed, the log message
- for the local Docker Compose setup, a hash of `container_id`, `created`, and `message`

Avoid deriving `sortId` only from low-cardinality fields such as `component`, only from `time`, or from values that can
change between reprocessing attempts. A UUID or generated event ID is acceptable if it is stored with the document and
remains unchanged on retries or reindexing. A deterministic hash is usually easier to reproduce when debugging.

## Collector Normalization

ORT Server currently queries the canonical field names listed above and does not support configuring alternate
Elasticsearch field names per deployment. Collector pipelines must therefore normalize platform-specific metadata and
log content into this schema before documents are indexed.

For Kubernetes deployments, this typically means deriving `namespace` and `component` from pod metadata, extracting
`ortRunId`, `level`, `time`, and `message` from the structured ORT Server log line, and adding `sortId` as described in
the query behavior section.

If the filter or sort fields are missing or mapped incompatibly, ORT Server queries will not find the expected log
documents. Hits without `_source.message` are skipped because there is no rendered log line to write to the downloaded
file.

An Elasticsearch index template for the required fields can look like this:

```json
{
  "mappings": {
    "properties": {
      "namespace": { "type": "keyword" },
      "component": { "type": "keyword" },
      "ortRunId": {
        "type": "keyword",
        "fields": {
          "numeric": { "type": "long" }
        }
      },
      "level": { "type": "keyword" },
      "time": {
        "type": "date",
        "format": "strict_date_optional_time||epoch_millis"
      },
      "sortId": { "type": "keyword" },
      "message": {
        "type": "text",
        "fields": {
          "keyword": {
            "type": "keyword",
            "ignore_above": 8191
          }
        }
      }
    }
  }
}
```
