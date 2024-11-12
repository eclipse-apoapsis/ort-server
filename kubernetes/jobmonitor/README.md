# Kubernetes Job Monitor Component

This module is an add-on to the [Kubernetes Transport](../../transport/kubernetes/README.md) implementation.
It implements robust job handling and cleanup of completed jobs.

## Synopsis

Workers spawned by the Orchestrator report their status - success or failure - on completion by sending a corresponding message back to the Orchestrator.
That way the Orchestrator can keep track on an ongoing ORT run and trigger the next steps to make progress.

In a distributed setup, however, there is always a chance that a worker job crashes completely before it can even send a failure message.
In that scenario, without any further means, the Orchestrator would not be aware of the (abnormal) termination of the job; thus the whole run would stall.

The purpose of this component is to prevent this by implementing an independent mechanism to detect failed jobs and sending corresponding notifications to the Orchestrator.
With this in place, it is guaranteed that the Orchestrator is always notified about the outcome of a job it has triggered.

## Functionality

For the detection of failed jobs, the Job Monitor component actually implements multiple strategies:

- It uses the [Kubernetes Watch API](https://kubernetes.io/docs/reference/using-api/api-concepts/#efficient-detection-of-changes) to receive notifications about changes in the current state of jobs.
  Based on such change events, it can detect failed jobs and act accordingly.
- In addition, it lists the currently active jobs periodically and inspects this list for failed jobs.
  This is done for the following reasons:
  - The scanning of jobs in regular intervals is a safety net in case a relevant change event was missed by the watching part.
    This could happen for instance if the monitor component was shortly down or gets restarted.
    It is then still guaranteed that the Orchestrator eventually receives a notification.
  - Based on the job list, it is possible to remove completed jobs and their associated pods.
    This is not done out-of-the-box by Kubernetes; so the set of completed jobs would permanently grow.
    Therefore, the monitor component does an automatic cleanup of older jobs.
- Practice has shown that the strategies described so far are still not sufficient to handle all potential failure scenarios:
  It is possible - probably related to certain infrastructure failures - that Kubernetes jobs simply disappear without a notification being received via the watch API.
  This effect can also be achieved by simply killing a job via a `kubectl delete job` command.
  Then also the safety net with listing the existing jobs and checking for failures does not help, since the affected jobs no longer exist.
  The ORT run owning the job would then never be marked as completed.
  Therefore, there is another component referred to as *lost jobs finder*, which basically does a periodic sync between the jobs that should be active according to the ORT Server database and the actual jobs running on Kubernetes.
  If this component detects jobs that are expected to be active on Kubernetes, but are missing, it notifies the Orchestrator about them, which can then act accordingly.

## Configuration

Some aspects of the component can be configured in the module’s configuration file or via environment variables.
The fragment below shows the available configuration options:

```
jobMonitor {
  namespace = "ortserver"
  enableWatching = true
  enableReaper = true
  reaperInterval = 600
  enableLostJobs = true
  lostJobsInterval = 120
  lostJobsMinAge = 30
}
```

The properties have the following meaning:

| Property         | Variable                      | Description                                                                                                                                                                                                                                                                                                                                                               |
|------------------|-------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| namespace        | MONITOR\_NAMESPACE            | Defines the namespace in which jobs are to be monitored. This is typically the same namespace this component is deployed in.                                                                                                                                                                                                                                              |
| enableWatching   | MONITOR\_WATCHING\_ENABLED    | A flag that controls whether the watching mechanism is enabled. If set to **false**, the component will not register itself as a watcher for job changes. This can be useful for instance in a test environment where failed jobs should not be cleaned up immediately.                                                                                                   |
| enableReaper     | MONITOR\_REAPER\_ENABLED      | A flag that controls whether the part that scans for completed and failed jobs periodically (aka the *Reaper*) is active. Again, it can be useful to disable this part to diagnose problems with failed jobs.                                                                                                                                                             |
| reaperInterval   | MONITOR\_REAPER\_INTERVAL     | The interval in which the periodic scans for completed and failed jobs are done (in seconds). This can be used to fine-tune the time completed jobs are kept.                                                                                                                                                                                                             |
| enableLostJobs   | MONITOR\_LOST\_JOBS\_ENABLED  | A flag that controls whether the lost jobs finder component is enabled. If this component is active, a valid database configuration must be provided as well.                                                                                                                                                                                                             |
| lostJobsInterval | MONITOR\_LOST\_JOBS\_INTERVAL | The interval in which the lost jobs finder component executes its checks (in seconds). Since a check requires some database queries, a balance has to be found between the load on the system caused by this and the delay of notifications sent to the Orchestrator. As the scenario of lost jobs should be rather rare, a longer interval is probably acceptable.       |
| lostJobsMinAge   | MONITOR\_LOST\_JOBS\_MIN\_AGE | The minimum age of a job (in seconds) to be taken into account by the lost jobs finder component. This setting addresses potential race conditions that might be caused by delays between creating an entry in the database and starting the corresponding job in Kubernetes; in an extreme case, a job would be considered as lost before it even started on Kubernetes. |

In addition to these options, the configuration must contain a section defining the [transport](../../transport/README.md) for sending notifications to the Orchestrator.
