# Configuration

This document gives an overview over the configuration options supported by ORT Server.
It also shortly describes the configuration mechanism in place.

:::note
In this context, the term _configuration_ refers to the static configuration of an ORT Server deployment, such as database settings, service credentials, etc.
It does not cover the configuration of single ORT runs that can be specified by users when triggering a run.
:::

## Configuration Mechanism

This section describes the way configuration options can be defined for ORT Server.

In general, ORT Server distinguishes between non-sensitive and sensitive configuration information.
To the first group belong options like URLs to services, or options that customize the behavior of services or ORT Server itself.
The second group is mainly related to credentials for infrastructure services; they require a special treatment, so that they are not exposed to the public.
These different options are discussed separately below.

### Non-sensitive Configuration Options

ORT Server uses the [Typesafe Config](https://github.com/lightbend/config) library to access configuration data that does not require special protection.
The library reads properties from files named `application.conf` that are in the [HOCON](https://github.com/lightbend/config/blob/main/HOCON.md).

The single ORT Server components have their own `application.conf` files that define their specific configuration settings.
Since such files are not intended to be overridden or manipulated by users, customization is done via environment variables:
`application.conf` files can contain references to environment variables.
Each property that can be customized is declared twice in the file, once with a default value and once referring to an environment variable.
The semantic is that if the referenced environment variable is defined, it overrides the default value; otherwise, the default is active.

So, basically, non-sensitive configuration options can be set via environment variables.

### Sensitive Configuration Options

When it comes to database credentials or credentials for other external systems or services, these may require additional effort to keep them confident.
While it is possible (and conform to the [Twelve-Factor principles](https://12factor.net/config)) to pass this information via environment variables, too, there can be other approaches that are more secure or convenient to use.
For instance, cloud providers typically offer dedicated secret storage services that provide an integration with their container services; so, secrets could be directly injected into containers.

To allow ORT Server to make use of such approaches, configuration options for secrets or credentials are accessed differently.
For this purpose, there is the dedicated [ConfigManager](https://github.com/eclipse-apoapsis/ort-server/blob/main/config/README.md) component offering corresponding functionality.
The component defines a `ConfigSecretProvider` interface that it uses to actually access secrets.
The integration of specific secret storage services can be done by providing a concrete implementation of this interface.
That way, high security requirements can be fulfilled.

For other environments that do not use a dedicated storage for secrets, there is a fallback mechanism that allows treating secrets analogously to other configuration options:
When a secret is requested from `ConfigManager`, it first checks whether a property with the same name exists in the regular configuration.
If so, it returns the value of this setting; otherwise, it delegates to the secret provider.
This means that the values of secrets can be set via environment variables in the same way as other configuration options.
The fallback mechanism is active by default, but can be disabled to enforce that secrets can only be obtained from a provider.
Refer to the [documentation](https://github.com/eclipse-apoapsis/ort-server/blob/main/config/README.md) of the configuration component for further information how to control this setting.

## Available Configuration Options

It would be nice to have a central list with all configuration options supported by ORT Server.
However, since the codebase is rapidly changing, it is rather difficult to maintain such a list and keep it in sync.
Therefore, this section basically contains references to the `application.conf` files used by the single ORT Server components.
They can be inspected to get a comprehensive list of all configuration options in use.

| Component              | Link                                                                                                                                   |
| ---------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| Core                   | [application.conf](https://github.com/eclipse-apoapsis/ort-server/blob/main/core/src/main/resources/application.conf)                  |
| Orchestrator           | [application.conf](https://github.com/eclipse-apoapsis/ort-server/blob/main/orchestrator/src/main/resources/application.conf)          |
| Config worker          | [application.conf](https://github.com/eclipse-apoapsis/ort-server/blob/main/workers/config/src/main/resources/application.conf)        |
| Analyzer worker        | [application.conf](https://github.com/eclipse-apoapsis/ort-server/blob/main/workers/analyzer/src/main/resources/application.conf)      |
| Advisor worker         | [application.conf](https://github.com/eclipse-apoapsis/ort-server/blob/main/workers/advisor/src/main/resources/application.conf)       |
| Scanner worker         | [application.conf](https://github.com/eclipse-apoapsis/ort-server/blob/main/workers/scanner/src/main/resources/application.conf)       |
| Evaluator worker       | [application.conf](https://github.com/eclipse-apoapsis/ort-server/blob/main/workers/evaluator/src/main/resources/application.conf)     |
| Reporter worker        | [application.conf](https://github.com/eclipse-apoapsis/ort-server/blob/main/workers/reporter/src/main/resources/application.conf)      |
| Kubernetes job monitor | [application.conf](https://github.com/eclipse-apoapsis/ort-server/blob/main/kubernetes/jobmonitor/src/main/resources/application.conf) |

In addition to these global options, ORT Server ships with different implementations for concrete service provider interfaces.
The modules for these implementations should contain README files that also describe the configuration options supported by them.

## Referenced Secrets

ORT Server itself requires only a small number of secrets for its global configuration.
They are listed in the table below.
Again, concrete implementations for service provider interfaces may require additional secrets.
In this case, their documentation should contain corresponding information.

| Secret key        | Module | Description                                                                                                                                                                                  |
| ----------------- | ------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| database.username | all    | The username to connect to the central database.                                                                                                                                             |
| database.password | all    | The password to connect to the central database.                                                                                                                                             |
| keycloakApiSecret | core   | The secret for the OAuth client to access the Keycloak API. This is used to create roles and groups in Keycloak dynamically for the organizations, products, and repositories in ORT Server. |
