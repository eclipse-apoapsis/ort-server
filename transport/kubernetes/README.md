# Kubernetes Transport Implementation

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

<table>
<colgroup>
<col style="width: 20%" />
<col style="width: 60%" />
<col style="width: 20%" />
</colgroup>
<thead>
<tr class="header">
<th>Property</th>
<th>Description</th>
<th>Default</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p>namespace</p></td>
<td><p>The namespace inside the Kubernetes cluster, in which the job will be created.</p></td>
<td><p>none</p></td>
</tr>
<tr class="even">
<td><p>imageName</p></td>
<td><p>The full name of the container image from which the pod is created, including the tag name. The value can contain variables that are resolved based on message properties.</p></td>
<td><p>none</p></td>
</tr>
<tr class="odd">
<td><p>imagePullPolicy</p></td>
<td><p>Defines the <a href="https://kubernetes.io/docs/concepts/containers/images/#image-pull-policy">pull policy</a> for the container image, one of <code>IfNotPresent</code>, <code>Always</code>, or <code>Never</code>.</p></td>
<td><p><code>Never</code></p></td>
</tr>
<tr class="even">
<td><p>imagePullSecret</p></td>
<td><p>The name of the secret to be used to connect to the container registry when pulling the container image. This is needed when using private registries that require authentication.</p></td>
<td><p>empty</p></td>
</tr>
<tr class="odd">
<td><p>restartPolicy</p></td>
<td><p>The <a href="https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#restart-policy">restart policy</a> for the container, one of <code>Always</code>, <code>OnFailure</code>, or <code>Never</code>.</p></td>
<td><p><code>OnFailure</code></p></td>
</tr>
<tr class="even">
<td><p>backoffLimit</p></td>
<td><p>Defines the <a href="https://kubernetes.io/docs/concepts/workloads/controllers/job/#pod-backoff-failure-policy">backup failure policy</a> for the job to be created. This is the number of retries for failed pods attempted by Kubernetes before it considers the job as failed.</p></td>
<td><p>2</p></td>
</tr>
<tr class="odd">
<td><p>commands</p></td>
<td><p>The commands to be executed in the container. This can be used to overwrite the container’s default command and corresponds to the <code>command</code> property in the <a href="https://kubernetes.io/docs/tasks/inject-data-application/define-command-argument-container/">Kubernetes pod configuration</a>. Here a string can be specified. To obtain the array with commands expected by Kubernetes, the string is split at whitespaces, unless the whitespace occurs in double quotes.</p></td>
<td><p>empty</p></td>
</tr>
<tr class="even">
<td><p>args</p></td>
<td><p>The arguments to be passed to the container’s start command. This corresponds to the <code>args</code> property in the <a href="https://kubernetes.io/docs/tasks/inject-data-application/define-command-argument-container/">Kubernetes pod configuration</a>. It is confusing that Kubernetes treats both properties, <code>command</code> and <code>args</code>, as arrays. In most examples, a single string is used as <code>command</code>, while multiple arguments can be provided in the <code>args</code> array. Analogously to the <code>commands</code> property, the string provided here is split at whitespaces to obtain the arguments. If a single argument contains whitespaces, it needs to be surrounded by double quotes.</p></td>
<td><p>empty</p></td>
</tr>
<tr class="odd">
<td><p>mountSecrets</p></td>
<td><p>With this property, it is possible to mount the contents of Kubernetes <a href="https://kubernetes.io/docs/concepts/configuration/secret/">secrets</a> as files into the resulting pod. The string is interpreted as a sequence of mount declarations separated by whitespace. Each mount declaration has the form <em>secret→mountPath</em>, where <em>secret</em> is the name of a secret, and <em>mountPath</em> is a path in the container where the content of the secret should be made available. On this path, for each key of the secret a file is created whose content is the value of the key. To achieve this, the Kubernetes Transport implementation generates corresponding <code>volume</code> and <code>volumeMount</code> declarations in the pod configuration. This mechanism is useful not only for secrets but also for other kinds of external data that should be accessible from a pod, for instance, custom certificates.</p></td>
<td><p>empty</p></td>
</tr>
<tr class="even">
<td><p>annotationVariables</p></td>
<td><p>It is often useful or necessary to declare specific annotations for the jobs that are created by the Kubernetes Transport implementation. This can be used for instance for documentation purposes, to group certain jobs, or to request specific services from the infrastructure. Since there can be an arbitrary number of annotations that also might become complex, it is difficult to use a single configuration property to define all annotations at once. (And using a dynamic set of configuration properties does not work well either with the typical approach configuration is read in ORT Server.)</p>
<p>To deal with these issues, the implementation introduces a level of indirection: The <em>annotationVariables</em> property does not contain the definitions for annotations itself, but only lists the names of environment variables - separated by comma - that declare annotations. Each referenced environment variable must have a value of the form <code>key=value</code>. The <em>key</em> becomes the key of the annotation, the <em>value</em> its value.</p>
<p>As a concrete example, if <em>annotationVariables</em> has the value</p>
annotationVariables = "VAR1, VAR2"
<p>and there are corresponding environment variables:</p>
VAR1=annotation1=value1 VAR2=annotation2=value2
<p>then the Kubernetes Transport implementation will produce a job declaration containing the following fragment:</p>
<div class="sourceCode" id="cb1"><pre class="sourceCode yaml"><code class="sourceCode yaml"><a class="sourceLine" id="cb1-1" title="1"><span class="fu">template:</span></a>
<a class="sourceLine" id="cb1-2" title="2">  <span class="fu">metadata:</span></a>
<a class="sourceLine" id="cb1-3" title="3">    <span class="fu">annotations:</span></a>
<a class="sourceLine" id="cb1-4" title="4">      <span class="fu">annotation1:</span><span class="at"> value1</span></a>
<a class="sourceLine" id="cb1-5" title="5">      <span class="fu">annotation2:</span><span class="at"> value2</span></a></code></pre></div>
<p>If variables are referenced that do not exist or do not contain an equals ('=') character in their value to separate the key from the value, a warning is logged, and those variables are ignored.</p></td>
<td><p>empty</p></td>
</tr>
<tr class="odd">
<td><p>serviceAccount</p></td>
<td><p>Allows specifying the name of a service account that is assigned to newly created pods. Service accounts can be used to grant specific permissions to pods.</p></td>
<td><p>null</p></td>
</tr>
<tr class="even">
<td><p>cpuRequest</p></td>
<td><p>Allows setting the request for the CPU resource. The value can contain variables that are resolved based on message properties.</p></td>
<td><p>undefined</p></td>
</tr>
<tr class="odd">
<td><p>cpuLimit</p></td>
<td><p>Allows setting the limit for the CPU resource. The value can contain variables that are resolved based on message properties.</p></td>
<td><p>undefined</p></td>
</tr>
<tr class="even">
<td><p>memoryRequest</p></td>
<td><p>Allows setting the request for the memory resource. The value can contain variables that are resolved based on message properties.</p></td>
<td><p>undefined</p></td>
</tr>
<tr class="odd">
<td><p>memoryLimit</p></td>
<td><p>Allows setting the limit for the memory resource. The value can contain variables that are resolved based on message properties.</p></td>
<td><p>undefined</p></td>
</tr>
</tbody>
</table>

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
