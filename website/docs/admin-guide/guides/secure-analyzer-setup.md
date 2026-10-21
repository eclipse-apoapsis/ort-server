# Securing the Analyzer

To determine the dependencies of a project, the Analyzer worker invokes the project's package managers.
Many package managers execute build scripts from the analyzed repository, so the Analyzer runs code that ORT Server does not control.
If that code is malicious, it can read everything that is available in its environment.

This guide describes how to run the Analyzer in a way that keeps infrastructure credentials, such as the database password or the credentials for the secret storage, out of reach of build scripts.

:::info
This setup requires the [Kubernetes Transport](../infrastructure/transport/kubernetes.md).
:::

## How It Works

By default, the Analyzer worker performs all of its work in a single container.
This container needs infrastructure credentials to read the job from the database, to resolve secrets, and to store the results.
As a consequence, these credentials are also available while build scripts are running.

To avoid this, the Analyzer worker can split a job into three phases.
Each phase runs in its own container of the Analyzer pod and receives only the credentials it needs:

| Phase       | Container                       | Tasks                                                                                                  | Requires access to                      |
| ----------- | ------------------------------- | ------------------------------------------------------------------------------------------------------ | --------------------------------------- |
| Preparation | `analyzerinit` (init container) | Reads the job, clones the repository, resolves secrets, and creates the package manager configuration. | Database, secret storage, configuration |
| Analysis    | `analyzer-main`                 | Runs the ORT Analyzer, which invokes the package managers and executes build scripts.                  | None of the infrastructure credentials  |
| Result      | `analyzerresult`                | Waits for the analysis to finish and stores the results in the database.                               | Database, configuration                 |

The containers exchange data through an `emptyDir` volume that is mounted at `/mnt/exchange` in all three containers.

:::note
The analysis container still has access to the credentials that the analyzed repository needs to download dependencies, for example, credentials for a private package registry.
The preparation phase writes them to the package manager configuration files.
Only the credentials for the [infrastructure services](../../user-guide/concepts/external-services.md) used by the repository are included.
:::

## What You Need to Configure

Running the phases in separate containers does not by itself remove infrastructure credentials from the analysis container.
Kubernetes provides credentials to a container in two ways, and you must restrict both:

- **Mounted files:**
  By default, every volume that is configured for the Analyzer is mounted into all containers of the pod, including the analysis container.
  To restrict a volume to the preparation and result containers, give it a name and assign it only to those containers.
- **Environment variables:**
  By default, the Kubernetes Transport copies all environment variables of the Orchestrator into the Analyzer containers.
  To restrict the variables the analysis container receives, define an allowlist.

Which of the two applies to your deployment depends on how you provide infrastructure credentials.
If you pass them as environment variables, which requires `allowSecretsFromConfig` to be enabled (the default), the allowlist protects them.
If you provide them as files, for example, through a mounted Kubernetes secret or persistent volume claim, only named volume mounts protect them.
Review both, as most deployments use a combination of the two.

:::warning
If you enable the phases without restricting volume mounts and environment variables, the Analyzer works as before, but the analysis container still has access to all infrastructure credentials.
:::

## Configuring the Helm Chart

If you deploy ORT Server with the [ORT Server Helm chart](https://github.com/eclipse-apoapsis/helm-charts), configure the phases with the following values:

| Value                                                               | Description                                                                                                                           |
| ------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| `transport.kubernetes.analyzer.phases.enabled`                      | Runs the Analyzer in three phases. Enabled by default.                                                                                |
| `transport.kubernetes.analyzer.phases.preparationVolumeMounts`      | Names of the volume mounts to add to the preparation container.                                                                       |
| `transport.kubernetes.analyzer.phases.resultVolumeMounts`           | Names of the volume mounts to add to the result container.                                                                            |
| `transport.kubernetes.analyzer.phases.analysisEnvironmentAllowlist` | Names of the environment variables that the analysis container inherits. The default is an empty list, so no variables are inherited. |

The chart creates the exchange volume and configures the containers automatically.
You only need to make sure that volumes containing infrastructure credentials are not mounted into the analysis container:

1. In `transport.kubernetes.analyzer.mountSecrets` and `transport.kubernetes.analyzer.mountPvcs`, prefix each mount that contains infrastructure credentials with a name followed by `=`.
2. Add the name to `preparationVolumeMounts`, `resultVolumeMounts`, or both, depending on which phases need the volume.
   If you are not sure, add it to both lists.
   This does not affect the protection of the analysis phase.
3. If the analysis requires specific environment variables from the Orchestrator, for example, to configure the log format or a proxy, add their names to `analysisEnvironmentAllowlist`.
   Do not add variables that contain infrastructure credentials.

The following example mounts the Kubernetes secret `ort-server-secrets` into the preparation and result containers only, and passes the `LOG_FORMAT` variable to the analysis container:

```yaml
transport:
  kubernetes:
    analyzer:
      mountSecrets: 'secrets=ort-server-secrets->/mnt/secrets'
      phases:
        enabled: true
        preparationVolumeMounts:
          - secrets
        resultVolumeMounts:
          - secrets
        analysisEnvironmentAllowlist:
          - LOG_FORMAT
```

Mounts without a name, such as `ort-server-secrets->/mnt/secrets`, are added to all three containers.
Use unnamed mounts only for data that does not need protection, for example, custom CA certificates.

## Configuring the Orchestrator Manually

If you do not use the Helm chart, configure the Analyzer pod through environment variables in the deployment manifest of the Orchestrator.
The Kubernetes Transport evaluates these variables when it creates the Analyzer pod.
For a description of the underlying options, see the [Kubernetes Transport](../infrastructure/transport/kubernetes.md) documentation.

The following example shows a complete configuration.
It assumes that the infrastructure credentials are stored in the Kubernetes secret `ort-server-secrets`:

```yaml
env:
  # Declare the volume for exchanging data between the phases.
  - name: ANALYZER_MOUNT_EMPTY_DIRS
    value: 'exchange->/mnt/exchange'

  # Declare the containers for the preparation and result phases.
  # The main container runs the analysis phase.
  - name: ANALYZER_ADDITIONAL_CONTAINERS
    value: 'analyzerinit,analyzerresult'
  - name: ANALYZERINIT_INIT_CONTAINER
    value: 'true'

  # Mount the infrastructure credentials into the preparation and result containers only.
  - name: ANALYZER_MOUNT_SECRETS
    value: 'secrets=ort-server-secrets->/mnt/secrets'
  - name: ANALYZERINIT_VOLUME_MOUNTS
    value: 'secrets'
  - name: ANALYZERRESULT_VOLUME_MOUNTS
    value: 'secrets'

  # Do not pass any environment variables of the Orchestrator to the analysis container.
  - name: ANALYZER_ENVIRONMENT_ALLOWLIST
    value: ''

  # Select the phase that each container runs.
  - name: ANALYZER_COMMANDS
    value: '/bin/sh'
  - name: ANALYZER_ARGS
    value: '-c "exec java $JAVA_OPTS -cp $(cat /app/jib-classpath-file) @/app/jib-main-class-file analysis /mnt/exchange /mnt/exchange/sync/ready"'
  - name: ANALYZERINIT_ARGS
    value: '-c "exec java $JAVA_OPTS -cp $(cat /app/jib-classpath-file) @/app/jib-main-class-file preparation /mnt/exchange"'
  - name: ANALYZERRESULT_ARGS
    value: '-c "/etc/analyzer_scripts/await.sh /mnt/exchange/sync/ready && exec java $JAVA_OPTS -cp $(cat /app/jib-classpath-file) @/app/jib-main-class-file result /mnt/exchange"'
```

Adapt the example to your deployment as follows:

- **Volume mounts:**
  Name every entry in `ANALYZER_MOUNT_SECRETS` and `ANALYZER_MOUNT_PVCS` that contains infrastructure credentials, and reference the names in `ANALYZERINIT_VOLUME_MOUNTS` and `ANALYZERRESULT_VOLUME_MOUNTS`.
  Both variables take a comma-separated list of names.
  Do not reference these names in `ANALYZER_VOLUME_MOUNTS`, which configures the analysis container.
- **Environment allowlist:**
  `ANALYZER_ENVIRONMENT_ALLOWLIST` takes a comma-separated list of variable names.
  The names refer to the variables as they appear in the Analyzer container, after the Orchestrator's `ANALYZER_` prefix has been removed.
  For example, to pass the value of the Orchestrator variable `ANALYZER_HOME`, add `HOME` to the list.
  An empty value passes no variables.
  If the variable is not defined at all, all variables are passed.
  The variables that the transport needs to deliver the job to the container are always set.
- **Paths and commands:**
  Keep the commands and arguments as shown.
  If you change the mount path of the exchange volume, update all arguments that refer to `/mnt/exchange` accordingly.

The allowlist applies only to the analysis container.
The preparation and result containers inherit all environment variables of the Orchestrator, as they require the infrastructure credentials.

## Verifying the Setup

After deploying the configuration, start an ORT run and inspect the Analyzer pod while the run is in progress:

1. List the containers of the pod and verify that it contains the init container `analyzerinit` and the containers `analyzer-main` and `analyzerresult`:

   ```shell
   kubectl get pod <analyzer-pod> -o jsonpath='{.spec.initContainers[*].name} {.spec.containers[*].name}'
   ```

2. Verify that the analysis container has no volume mounts that contain infrastructure credentials:

   ```shell
   kubectl get pod <analyzer-pod> -o jsonpath='{.spec.containers[?(@.name=="analyzer-main")].volumeMounts}'
   ```

3. Verify that the analysis container has no environment variables that contain infrastructure credentials:

   ```shell
   kubectl get pod <analyzer-pod> -o jsonpath='{.spec.containers[?(@.name=="analyzer-main")].env[*].name}'
   ```

The log of each container states which phase it runs.
Apart from that, the results of an ORT run are the same as with the single-container setup.
