# Kubernetes

This module provides an implementation of the transport abstraction layer using the [Kubernetes Java Client](https://github.com/kubernetes-client/java/).

## Synopsis

The Kubernetes transport abstraction layer exchanges messages via environment variables.
The sender creates a Kubernetes Job using the Kubernetes API, that runs the configured container image and sets the message content as environment variables.
The container started by the Kubernetes Job acts as the receiver and constructs the message from the environment variables.
The implementation supports a number of configuration options to customize the resulting Kubernetes Job, for instance, to mount secrets or persistent volumes, or to set resource requests and limits.

## Configuration

To use this module, the `type` property in the transport configuration must be set to `kubernetes`.
For the sender part, a number of properties can be provided to configure the resulting pods as shown in the fragment below:

```
endpoint {
    sender {
        type = "kubernetes"
        namespace = "namespace-inside-cluster"
        imageName = "image-to-run"
        imagePullPolicy = "Always"
        imagePullSecret = "my-secret"
        restartPolicy = ""
        backoffLimit = 5
        commands = "/bin/sh"
        args = "-c java my.pkg.MyClass"
        mountSecrets = "secretVolume=server-secrets->/mnt/secrets server-certificates->/mnt/certificates"
        mountPvcs = "my-pvc->/mnt/data,W"
        mountEmptyDirs = "shared->/mnt/shared"
        labels = "label1=value1,label2=value2"
        annotationVariables = "ANNOTATION_VAR1, ANNOTATION_VAR2"
        serviceAccount = "my_service_account"
        cpuRequest = 250m
        cpuLimit = 500m
        memoryRequest = 64Mi
        memoryLimit = 128Mi
        additionalContainers = "init-container, sidecar-container"
        volumeMounts = "secretVolume"
    }
}
```

The properties can be grouped into different categories, as described in the following sections.

### Pod-related properties

The properties in this category define the characteristics of the pod as a whole that is created by the sender.

#### `additionalContainers`

**Default: `empty`**

The Kubernetes Transport implementation supports adding additional containers to the pod created by the sender. This can be used to enable advanced use cases, for instance, to run an init container that prepares the environment for the main container, or to run a sidecar container that provides additional services to the main container.

Declaring additional containers is done by providing a comma-separated list of container names in the `additionalContainers` property. While the properties of the main container are configured via the properties described above, additional containers take their configuration from environment variables: For each container, a set of environment variables starting with the name of the container in upper case followed by an underscore is expected. There are then corresponding variables for each property of the container, using snake-case for the property name instead of camelCase. For instance, for a container named _sidecar_, there can be variables like `SIDECAR_IMAGE_NAME` for the `imageName` property or `SIDECAR_CPU_LIMIT` for the `cpuLimit` property. (Refer to the section about container-related properties for a full list of available configuration properties.) These variables are optional; for missing variables, the value from the main container is used with the following exceptions:

- Resource requests and limits are not copied from the main container. They should be set explicitly for each additional container if needed.
- Volume mounts are undefined as well if they are not set explicitly.

The variable `<CONTAINER_NAME>_INIT_CONTAINER=TRUE` can be used to declare a container as init container. If this variable is not set or has a different value, the container is treated as a regular container.

#### `annotationVariables`

**Default: `empty`**

It is often useful or necessary to declare specific annotations for the jobs that are created by the Kubernetes Transport implementation.
This can be used for instance for documentation purposes, to group certain jobs, or to request specific services from the infrastructure.
Since there can be an arbitrary number of annotations that also might become complex, it is difficult to use a single configuration property to define all annotations at once.
And using a dynamic set of configuration properties does not work well either with the typical approach configuration is read in ORT Server.

To deal with these issues, the implementation introduces a level of indirection:
The `annotationVariables` property does not contain the definitions for annotations itself, but only lists the names of environment variables, separated by comma, that declare annotations.
Each referenced environment variable must have a value of the form `key=value`.
The `key` becomes the key of the annotation, the `value` its value.

As a concrete example, if `annotationVariables` has the value `VAR1, VAR2` and there are corresponding environment variables `VAR1=annotation1=value1` and `VAR2=annotation2=value2`, then the Kubernetes Transport implementation will produce a job declaration containing the following fragment:

```yaml
template:
  metadata:
    annotations:
      annotation1: value1
      annotation2: value2
```

If variables are referenced that do not exist or do not contain an equals (`=`) character in their value to separate the key from the value, a warning is logged, and those variables are ignored.

#### `backoffLimit`

**Default: `2`**

Defines the [backup failure policy](https://kubernetes.io/docs/concepts/workloads/controllers/job/#pod-backoff-failure-policy) for the job to be created.
This is the number of retries for failed pods attempted by Kubernetes before it considers the job as failed.

#### `imagePullSecret`

**Default: `empty`**

The name of the secret to be used to connect to the container registry when pulling the container image.
This is needed when using private registries that require authentication.

#### `labels`

**Default: `empty`**

A comma-separated list of labels to be added to the job.
Labels are key-value pairs and must have the form `key=value`.
The labels `ort-worker`, `run-id` and any `trace-id-*` are inserted automatically by the Kubernetes sender and cannot be overridden; matching entries in this list are ignored.

#### `namespace`

**Default: `none`**

The namespace inside the Kubernetes cluster, in which the job will be created.

#### `restartPolicy`

**Default: `OnFailure`**

The [restart policy](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#restart-policy) for the container, one of `Always`, `OnFailure`, or `Never`.

#### `serviceAccount`

**Default: `null`**

Allows specifying the name of a service account that is assigned to newly created pods.
Service accounts can be used to grant specific permissions to pods.

#### `volumeMounts`

**Default: `empty`**

When additional containers are declared, this property can be used to specify which of the volumes should be mounted into which container. This makes it possible for instance, to exchange data between two containers via a shared volume, or to restrict volumes with secrets to specific containers.

To make use of this property, as a first step, volume mounts must be assigned a name. This is simply done by adding the name of the volume mount as a prefix to the mount declaration, separated by an equals sign. Such a name can be provided for all types of volume mounts. For instance, the declaration of a secret volume mount could look like `secretVolume=secretName->/mnt/secrets`. It is then known under the name _secretVolume_.

As a second step, the `volumeMounts` property of the main container, or the `<CONTAINER_NAME>_VOLUME_MOUNTS` environment variable for an additional container, can reference these names to select the volume mounts to be used in the respective container. Its value is a comma-separated list of volume mount names.

Volume mounts without a name are automatically added to all containers.

### Container-related properties

The properties in this category define various options of containers that are created by the sender. Per default, there is a single container in the pod that is configured via the properties listed in this section. It is, however, possible to add additional containers to the pod, as described under the `additionalContainers` property above. Such additional containers can be configured via environment variables, whose names are derived from the properties described in this section.

#### `args`

**Default: `empty`**

The arguments to be passed to the container’s start command.
This corresponds to the `args` property in the [Kubernetes pod configuration](https://kubernetes.io/docs/tasks/inject-data-application/define-command-argument-container/).
It is confusing that Kubernetes treats both properties, `command` and `args`, as arrays.
In most examples, a single string is used as `command`, while multiple arguments can be provided in the `args` array.
Analogously to the `commands` property, the string provided here is split at whitespaces to obtain the arguments.
If a single argument contains whitespaces, it needs to be surrounded by double quotes.

#### `commands`

**Default: `empty`**

The commands to be executed in the container.
This can be used to overwrite the container’s default command and corresponds to the `command` property in the [Kubernetes pod configuration](https://kubernetes.io/docs/tasks/inject-data-application/define-command-argument-container/).
Here a string can be specified.
To obtain the array with commands expected by Kubernetes, the string is split at whitespaces, unless the whitespace occurs in double quotes.

#### `cpuLimit`

**Default: `undefined`**

Allows setting the limit for the CPU resource.
The value can contain variables that are resolved based on message properties.

#### `cpuRequest`

**Default: `undefined`**

Allows setting the request for the CPU resource.
The value can contain variables that are resolved based on message properties.

#### `imageName`

**Default: `none`**

The full name of the container image from which the pod is created, including the tag name.
The value can contain variables that are resolved based on message properties.

#### `imagePullPolicy`

**Default: `Never`**

Defines the [pull policy](https://kubernetes.io/docs/concepts/containers/images/#image-pull-policy) for the container image, one of `IfNotPresent`, `Always`, or `Never`.

#### `memoryLimit`

**Default: `undefined`**

Allows setting the limit for the memory resource.
The value can contain variables that are resolved based on message properties.

While the configuration is static for a deployment of ORT Server, there are use cases that require changing some settings dynamically for a specific ORT run.
For instance, if the run processes a large repository, the memory limits might need to be increased.
To make this possible, the values of some properties can contain variables that are resolved from the properties of the current message.
The table above indicates which properties support this mechanism.
Variables follow the popular syntax `$+{variable}+`.

To give an example, an excerpt from the configuration could look as follows:

```
endpoint {
    sender {
        type = "kubernetes"
        memoryLimit = ${memory}
        ...
    }
}
```

If the message now has the following set in its `transportProperties`:

```
kubernetes.memory = 768M
```

Then the memory limit of the pod to be created will be set to 768 megabytes.

#### `memoryRequest`

**Default: `undefined`**

Allows setting the request for the memory resource.
The value can contain variables that are resolved based on message properties.

#### `mountEmptyDirs`

**Default: `empty`**

With this property, it is possible to mount [emptyDir](https://kubernetes.io/docs/concepts/storage/volumes/#emptydir) volumes into the resulting pod.
The string is interpreted as a sequence of mount declarations separated by whitespace.
Each mount declaration has the form `name->mountPath`, where `name` is the name of the empty dir volume and `mountPath` is the path in the container where the volume should be mounted.
To achieve this, the Kubernetes Transport implementation generates corresponding `volume` and `volumeMount` declarations in the pod configuration.

#### `mountPvcs`

**Default: `empty`**

With this property, it is possible to mount [PersistentVolumeClaims](https://kubernetes.io/docs/concepts/storage/persistent-volumes/) into the resulting pod.
The string is interpreted as a sequence of mount declarations separated by whitespace.
Each mount declaration has the form `pvcName->mountPath,access`, where `pvcName` is the name of the persistent volume claim, `mountPath` is the path in the container where the volume should be mounted, and `access` is either `R` (read-only) or `W` (writable).
To achieve this, the Kubernetes Transport implementation generates corresponding `volume` and `volumeMount` declarations in the pod configuration.

#### `mountSecrets`

**Default: `empty`**

With this property, it is possible to mount the contents of Kubernetes [secrets](https://kubernetes.io/docs/concepts/configuration/secret/) as files into the resulting pod.
The string is interpreted as a sequence of mount declarations separated by whitespace.
Each mount declaration has the form `secret→mountPath`, where `secret` is the name of a secret, and `mountPath` is a path in the container where the content of the secret should be made available.
On this path, for each key of the secret a file is created whose content is the value of the key.
To achieve this, the Kubernetes Transport implementation generates corresponding `volume` and `volumeMount` declarations in the pod configuration.
This mechanism is useful not only for secrets but also for other kinds of external data that should be accessible from a pod, for instance, custom certificates.

> [!NOTE]
> The receiver part does not need any specific configuration settings except for the transport type itself.

## Inheritance of environment variables

Per default, when creating a new job, the `KubernetesMessageSender` passes all environment variables defined for the current pod to the specification of the new job.
That way, common variables like service credentials can be shared between pods.

A problem can arise though if there are name clashes with environment variables, e.g., if the new job requires a different value in a variable than the current pod.
To address such problems, the Kubernetes transport protocol supports a simple mapping mechanism for variable names that start with a prefix derived from the target endpoint:
When setting up the environment variables for the new job, it checks for variables whose name starts with the prefix name of the target endpoint in capital letters followed by an underscore.
This prefix is then removed from the variable in the environment of the new job.

For instance, to set the `HOME` variable for the Analyzer worker to a specific value, define a variable `ANALYZER_HOME` in the Orchestrator pod.
When then a new Analyzer job is created, its `HOME` variable get initialized from the value of the `ANALYZER_HOME` variable.
An existing `HOME` variable in the Orchestrator pod will not conflict with this other value.
