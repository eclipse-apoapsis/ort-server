# Projections

Projections are denormalized read models that are built for a single ORT run when they are first requested.
Denormalized means that data that is spread over many tables of the normalized schema is copied into a single table, so that it can be read without joins.
They are meant for endpoints that would otherwise need complex queries across many tables of the normalized schema, for example, to filter and sort the data shown in the UI.
A projection stores the data of an endpoint in a "wide" table, with plain indexed columns for all fields that are filtered or sorted, and `JSONB` columns for nested data.

Projections are built on demand, because many runs, for example those triggered by CI pipelines, are never viewed in the UI.
The code is located in the `components/projections` module.

## How It Works

The `projection_registry` table tracks the status of each projection, identified by the ID of the ORT run and the type of the projection.
It also serves as the work queue for building projections, so no message bus is needed.

When an endpoint requests a projection from the `ProjectionService`, one of the following happens:

- If the projection is ready and was built for the current schema version, the endpoint can query its data.
- If the projection is missing or outdated, a build is requested.
  The build is started immediately in the background if the instance has capacity for it.
- If a build is running, the request waits for it to finish for a configured time, regardless of which instance runs it.
  This allows answering requests for small projections right away.
  Clients opt in to asynchronous responses with the `Prefer: respond-async` header.
  For them, the request waits only for a short time, and if the build is still running after that time, the endpoint should answer with `202 Accepted` and a `Retry-After` header.
  Other clients wait longer, and if the build is still running after that time, the endpoint should answer with `503 Service Unavailable` and a `Retry-After` header.
- If the last build failed, the endpoint should report the error.
  A new build is requested by the first request after the retry delay has passed.

Builds are claimed atomically and protected by a lease, so that multiple instances of the core do not build the same projection at the same time.
The lease is renewed while the build is running.
If an instance crashes, its lease expires and the build is taken over by another instance.
A recovery loop in each instance also picks up builds that could not be started immediately, and retries failed builds and builds with an expired lease up to a configured number of attempts.
After that, builds with an expired lease are marked as failed, so that a build that crashes the instances running it is not retried forever.
Like other failed builds, they are built again on the next request.

Schema versions only move forward.
Instances only claim builds that were requested for the schema version of their own builder, and a request for a newer schema version replaces a projection for an older one, even while it is being built.
During a rolling upgrade, when instances with different versions of a builder run at the same time, instances with the older version neither use nor rebuild projections for the newer version, but report them as being built until they are replaced.
A request on an instance with the older version that checked the projection right before it was replaced can still read the data of the newer version once.

The data of a projection is written in the same transaction that marks the projection as ready.
Marking it as ready only succeeds as long as the projection is being built by the same instance, otherwise the transaction is rolled back.
This ensures that a projection is only visible once all its data is committed, and that only one build is committed if several builds of the same projection run at the same time, for example after a lease expired.
The correctness of projections therefore does not depend on the lease, which only prevents duplicate work.

The `delete-unused-projections` maintenance task deletes projections that have not been accessed for a configured number of days.
Running builds are kept, but builds with an expired lease are deleted, so that builds for schema versions that no instance builds anymore do not remain forever.
Projections are also deleted together with their ORT run.

## Metrics

The `ProjectionService` provides the following metrics, which the core publishes together with its other metrics.
All of them have a `type` tag for the type of the projection.

| Metric                 | Type  | Additional tags | Description                                                             |
| ---------------------- | ----- | --------------- | ----------------------------------------------------------------------- |
| `projections.builds`   | Timer | `outcome`       | The duration of builds, by outcome: `ready`, `failed`, or `lease_lost`. |
| `projections.requests` | Timer | `result`        | The duration of requests including the wait for builds, by result.      |
| `projections.status`   | Gauge | `status`        | The number of projections in each status, updated by the recovery loop. |

A growing number of pending or failed projections indicates that builds are stuck or failing.

## Adding a Projection

To add a new type of projection:

1. Add a migration for the projection table.
   The table must contain the `ort_run_id` and a `projection_type` column that is restricted to the type of the projection, and reference the registry with `ON DELETE CASCADE`, so that the data is deleted together with the registry entry:

   ```sql
   CREATE TABLE projected_examples
   (
       ort_run_id      BIGINT NOT NULL,
       projection_type TEXT   NOT NULL DEFAULT 'example' CHECK (projection_type = 'example'),
       -- The columns of the projection.

       FOREIGN KEY (ort_run_id, projection_type) REFERENCES projection_registry ON DELETE CASCADE
   );
   ```

2. Implement a `ProjectionBuilder` and register it in the Koin module of the core.
   The `build` function is called inside a transaction and must replace any existing data of the run, because a projection can be rebuilt.
3. In the endpoint, request the projection from the `ProjectionService` and only query the projection table if it is ready.
   Data that can change after the run has finished, for example resolutions managed on the server, must not be stored in the projection, but has to be applied when reading.

Increment the `schemaVersion` of the builder whenever the data stored for a projection changes, for example, when columns are added or computed differently.
Existing projections are then rebuilt on their next request.
If the table itself changes, a migration is still required, but it does not need to migrate any data: it can simply delete the projections of the affected type, since they are rebuilt on demand.

## Configuration

The building of projections is configured in the `projections` section of the core configuration:

| Property                  | Environment variable                    | Default | Description                                                                |
| ------------------------- | --------------------------------------- | ------- | -------------------------------------------------------------------------- |
| `leaseDurationSeconds`    | `PROJECTIONS_LEASE_DURATION_SECONDS`    | 300     | The duration of a lease on a build.                                        |
| `maxParallelBuilds`       | `PROJECTIONS_MAX_PARALLEL_BUILDS`       | 2       | The maximum number of builds running in parallel in one instance.          |
| `recoveryIntervalSeconds` | `PROJECTIONS_RECOVERY_INTERVAL_SECONDS` | 30      | The interval for claiming builds that were not started or were abandoned.  |
| `maxAttempts`             | `PROJECTIONS_MAX_ATTEMPTS`              | 3       | The maximum number of attempts before a failed build is not retried.       |
| `retryDelaySeconds`       | `PROJECTIONS_RETRY_DELAY_SECONDS`       | 60      | The delay after which a failed build is retried.                           |
| `asyncRequestWaitMillis`  | `PROJECTIONS_ASYNC_REQUEST_WAIT_MILLIS` | 2000    | The maximum time a request with `Prefer: respond-async` waits for a build. |
| `syncRequestWaitMillis`   | `PROJECTIONS_SYNC_REQUEST_WAIT_MILLIS`  | 30000   | The maximum time other requests wait for a build to finish.                |

The retention of unused projections is configured in the `projections` section of the maintenance tasks configuration:

| Property        | Environment variable         | Default | Description                                                                      |
| --------------- | ---------------------------- | ------- | -------------------------------------------------------------------------------- |
| `retentionDays` | `PROJECTIONS_RETENTION_DAYS` | 30      | The number of days to keep projections after their last use. Must be at least 2. |
