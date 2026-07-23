# Hardening the Analyzer against malicious Input

This document describes a way how to configure the Analyzer worker to offer a reduced attack vector against malicious code in build scripts to be analyzed.

## Problem

When invoking package managers to analyze the dependencies of a project, it is not possible to prevent the execution of external code in all cases. For some package managers, build scripts are just code that gets executed; and even if this code comes from trusted sources (within the same company), there is always the danger that malicious code gets in that can do harm. Recent examples have shown that supply chain attacks become more and more sophisticated in their attempts to steal passwords and gather sensible information. This makes the environment in which the Analyzer operates especially insecure. Therefore, it is important to protected credentials as far as possible.

Before starting the analysis, the Analyzer sets up configuration files for the supported package managers with the credentials required for the current run. These are needed to download package metadata from private repositories. Therefore, the affected credentials must be present during the Analyzer run. The good thing is that - depending on the configuration of the project - only the subset of infrastructure services can be exposed that is actually used by the current repository. This is a factor to limit the impact of a successful attack.

What is problematic in the default configuration is the fact that also all infrastructure credentials are accessible in some form. To obtain the credentials for the package manager configuration files, the Analyzer needs to interact with the secret storage which stores the customer credentials. It also needs the credentials to the database to persist the generated results. In which form these credentials are made available, depends on the concrete implementation of the secrets manager in use. In addition, there are different implementations for secret stores as well. (ORT Server supports abstractions over concrete infrastructure components, so that it can be adapted to a specific target environment. Unfortunately, this flexibility does not make it easier to harden it against potential attacks.) The infrastructure secrets may be stored for instance in a file mounted at a specific path into the local filesystem. With infrastructure secrets being available in the Analyzer environment, there is a chance for malicious code executed during the analysis phase to obtain such secrets and by that get access to potentially critical infrastructure components.

## Solution Idea

The component that interacts with package managers - and is therefore exposed to potential malicious code - is ORT's [Analyzer](https://oss-review-toolkit.org/ort/docs/tools/analyzer) to which the Analyzer worker delegates. To do its work, this component needs a certain set of credentials to access dependencies and their metadata from private repositories - but not more. Especially, infrastructure components like the database or the secret store are not needed for the actual analysis after all package manager configuration files have been set up.

Therefore, the idea is to split the Analyzer worker into multiple phases:

- A preparation phase that sets up the environment for the actual analysis.
- An analysis phase that executes the actual analysis by invoking the ORT Analyzer component.
- A result phase that collects the analysis results and persists them to the database.

These phases can now be executed sequentially with different configurations to limit the set of credentials available in each phase to the required minimum. When deployed on Kubernetes (which is the recommended target platform for a production ORT Server deployment), the single phases can run in different containers in the Analyzer pod. In this setup, each container can be configured with exactly the secrets and services it needs. To exchange data between the phases, a shared volume can be used. ORT Server supports such a setup by providing the following features:

- The Analyzer worker can be instructed to run in a specific phase. This is done by passing specific parameters to the container entrypoint that select the phase and define the path to a shared volume for data exchange.
- The [Kubernetes Transport](../infrastructure/transport/kubernetes.md) implementation supports the required configuration options to construct an Analyzer pod running in multiple containers. For instance, it allows defining multiple containers, configuring the arguments to pass to the containers' entrypoints, declaring volumes of different types and how to mount them into the containers.

The remaining part of this document describes how to achieve a secured Analyzer execution on Kubernetes based on this idea in detail.

## Implementation

_Note: ORT Server can be configured to use different secret storage implementations. Therefore, this section cannot provide detailed settings for this part of the configuration. It will focus on the concrete steps to secure the Analyzer worker and give general guidelines about which secrets need to be made available in which phase._

The full configuration of the pod for the Analyzer worker is located in the deployment manifest for the _Orchestrator_. Here, a larger number of environment variables are defined that are evaluated by the Kubernetes Transport implementation and determine various aspects of the pod. If not noted otherwise, the code fragments shown below represent such variable definitions and therefore need to be placed into the `env:` section of the deployment manifest for Orchestrator. (To simplify the description, the variable values are provided explicitly; it is typically good practice to define them in a `values.yaml` file.)

Compared to the default configuration which runs the Analyzer worker in a single container, the following changes are required for a multiphase setup:

### Declare the shared volume

The different phases of the Analyzer worker exchange data via files written to a shared volume. This is a Kubernetes volume of type `emptyDir`. This volume and its mount path has to be declared for the Kubernetes Transport, which is done via the `ANALYZER_MOUNT_EMPTY_DIRS` environment variable in the following way:

```yaml
- name: ANALYZER_MOUNT_EMPTY_DIRS
  value: 'exchange->/mnt/exchange'
```

Based on this declaration, the Kubernetes Transport will create a volume named `exchange` of type `emptyDir` and mount it into all containers of the Analyzer pod at the path `/mnt/exchange`.

### Declare the containers for the different phases

In the multiphase setup, the main container (which is created automatically by the Kubernetes Transport) runs the _analysis_ phase. Now two additional containers have to be declared for the _preparation_ and _result_ phases. For this purpose, the transport supports the `ADDITIONAL_CONTAINERS` variable, which is expected to contain a comma-separated list of the names for the additional containers to create. Further properties of these containers can then be set via environment variables that are prefixed with the container name.

The container for the preparation phase should be an [init container](https://kubernetes.io/docs/concepts/workloads/pods/init-containers/). Kubernetes then ensures that this container runs before the other containers. The following fragment shows the declaration of the containers for the preparation and result phases:

```yaml
- name: ANALYZER_ADDITIONAL_CONTAINERS
  value: 'analyzerinit,analyzerresult'
- name: ANALYZERINIT_INIT_CONTAINER
  value: 'true'
```

Both containers require access to infrastructure secrets. How this is configured depends on the concrete implementation of the secret store in use. As an example, we assume that the secret store expects a Kubernetes secret to be mounted into the container at a specific path. This would require one variable with a named secret volume declaration and further variables for the two containers in which to mount the volume:

```yaml
- name: ANALYZER_MOUNT_SECRETS
  value: 'secrets=ort-server-secrets->/mnt/secrets|secrets'
- name: ANALYZERINIT_VOLUME_MOUNTS
  value: 'secrets'
- name: ANALYZERRESULT_VOLUME_MOUNTS
  value: 'secrets'
```

This declaration basically states that a secret volume named `secrets` is created from the Kubernetes secret `ort-server-secrets` and mounted into the preparation and result containers at the path `/mnt/secrets`. _Note: It is important to use a named volume declaration here; volumes without a name are mounted into all containers of the pod, which would make the secrets available in the analysis phase as well._

### Configure the entrypoints for the containers

The three containers defined for the Analyzer pod are all using the same container image for the Analyzer worker. The final step of the secure Analyzer configuration is to tell the containers via command line arguments which phase they should run and where to find the shared volume for data exchange.

The default setup does not require any command line arguments to be passed to the container entrypoint. In this case, the main Java class of the Analyzer worker is executed. When command line arguments are passed, the configuration becomes more complex. In the container, the properties `commands` and `args` need to be set:

- `commands` points to the shell allowing the execution of arbitrary commands.
- `args` contains the command(s) to be executed by the shell, which is the Java launcher with the classpath, the main class, and the command line arguments for the main class. Fortunately, the classpath and the main class do not have to be crafted manually; there are files generated during the build of the container image that contain these values and can be referenced from the command line.

The following fragment shows the part of the configuration concerned with the command line arguments for the three containers:

```yaml
- name: ANALYZER_COMMANDS
  value: '/bin/sh'
- name: ANALYZER_ARGS
  value: "-c \"exec java $JAVA_OPTS -cp $( cat /app/jib-classpath-file ) @/app/jib-main-class-file\ analysis /mnt/exchange /mnt/exchange/sync/ready\""
- name: ANALYZERINIT_ARGS
  value: "-c \"exec java $JAVA_OPTS -cp $( cat /app/jib-classpath-file ) @/app/jib-main-class-file\ preparation /mnt/exchange\""
- name: ANALYZERRESULT_ARGS
  value: "-c \"/etc/analyzer_scripts/await.sh /mnt/exchange/sync/ready && exec java $JAVA_OPTS -cp $( cat /app/jib-classpath-file ) @/app/jib-main-class-file\ result /mnt/exchange\""
```

This configuration tells the three containers which phase to execute and where to find the shared volume for data exchange - by constructing the `java` command from the classpath and main class files and passing the corresponding command line arguments. It also addresses the issue that the result phase should not start before the analysis phase has finished. This is not directly supported by Kubernetes; therefore, the Analyzer container image contains a small shell script `await.sh` that waits for a specific file to be created in the shared volume. The analysis phase creates this file when it has finished, which then allows the result phase to start.

Using this configuration, the logs of the Analyzer pod will show three invocations of the entry point. The log output includes the current phase and the command line arguments provided.
From the outside, no difference in the behavior of the Analyzer worker can be observed.
