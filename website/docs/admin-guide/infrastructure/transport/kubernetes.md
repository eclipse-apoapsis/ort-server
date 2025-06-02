# Kubernetes

This module provides an implementation of the transport abstraction layer using the [Kubernetes Java Client](https://github.com/kubernetes-client/java/).

## Synopsis

The Kubernetes transport abstraction layer exchanges messages via environment variables.
The sender creates a Kubernetes Job using the Kubernetes API, that runs the configured container image and sets the message content as environment variables.
The container started by the Kubernetes Job acts as the receiver and constructs the message from the environment variables.

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
        mountSecrets = "server-secrets->/mnt/secrets server-certificates->/mnt/certificates"
        annotationVariables = "ANNOTATION_VAR1, ANNOTATION_VAR2"
        serviceAccount = "my_service_account"
        cpuRequest = 250m
        cpuLimit = 500m
        memoryRequest = 64Mi
        memoryLimit = 128Mi
    }
}
```

The properties have the following meaning:

### `namespace`

**Default: `none`**

The namespace inside the Kubernetes cluster, in which the job will be created.

### `imageName`

**Default: `none`**

The full name of the container image from which the pod is created, including the tag name.
The value can contain variables that are resolved based on message properties.

### `imagePullPolicy`

**Default: `Never`**

Defines the [pull policy](https://kubernetes.io/docs/concepts/containers/images/#image-pull-policy) for the container image, one of `IfNotPresent`, `Always`, or `Never`.

### `imagePullSecret`

**Default: `empty`**

The name of the secret to be used to connect to the container registry when pulling the container image.
This is needed when using private registries that require authentication.

### `restartPolicy`

**Default: `OnFailure`**

The [restart policy](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#restart-policy) for the container, one of `Always`, `OnFailure`, or `Never`.

### `backoffLimit`

**Default: `2`**

Defines the [backup failure policy](https://kubernetes.io/docs/concepts/workloads/controllers/job/#pod-backoff-failure-policy) for the job to be created.
This is the number of retries for failed pods attempted by Kubernetes before it considers the job as failed.

### `commands`

**Default: `empty`**

The commands to be executed in the container.
This can be used to overwrite the container’s default command and corresponds to the `command` property in the [Kubernetes pod configuration](https://kubernetes.io/docs/tasks/inject-data-application/define-command-argument-container/).
Here a string can be specified.
To obtain the array with commands expected by Kubernetes, the string is split at whitespaces, unless the whitespace occurs in double quotes.

### `args`

**Default: `empty`**

The arguments to be passed to the container’s start command.
This corresponds to the `args` property in the [Kubernetes pod configuration](https://kubernetes.io/docs/tasks/inject-data-application/define-command-argument-container/).
It is confusing that Kubernetes treats both properties, `command` and `args`, as arrays.
In most examples, a single string is used as `command`, while multiple arguments can be provided in the `args` array.
Analogously to the `commands` property, the string provided here is split at whitespaces to obtain the arguments.
If a single argument contains whitespaces, it needs to be surrounded by double quotes.

### `mountSecrets`

**Default: `empty`**

With this property, it is possible to mount the contents of Kubernetes [secrets](https://kubernetes.io/docs/concepts/configuration/secret/) as files into the resulting pod.
The string is interpreted as a sequence of mount declarations separated by whitespace.
Each mount declaration has the form `secret→mountPath`, where `secret` is the name of a secret, and `mountPath` is a path in the container where the content of the secret should be made available.
On this path, for each key of the secret a file is created whose content is the value of the key.
To achieve this, the Kubernetes Transport implementation generates corresponding `volume` and `volumeMount` declarations in the pod configuration.
This mechanism is useful not only for secrets but also for other kinds of external data that should be accessible from a pod, for instance, custom certificates.

### `annotationVariables`

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

### `serviceAccount`

**Default: `null`**

Allows specifying the name of a service account that is assigned to newly created pods.
Service accounts can be used to grant specific permissions to pods.

### `cpuRequest`

**Default: `undefined`**

Allows setting the request for the CPU resource.
The value can contain variables that are resolved based on message properties.

### `cpuLimit`

**Default: `undefined`**

Allows setting the limit for the CPU resource.
The value can contain variables that are resolved based on message properties.

### `memoryRequest`

**Default: `undefined`**

Allows setting the request for the memory resource.
The value can contain variables that are resolved based on message properties.

### `memoryLimit`

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
