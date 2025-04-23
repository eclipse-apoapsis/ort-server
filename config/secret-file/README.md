# File-based config secret provider

This module provides an implementation of the `ConfigSecretProvider` interface defined by the [Configuration abstraction](../README.md) that reads secret values from files.

## Synopsis

The implementation is located in the [ConfigSecretFileProvider](src/main/kotlin/ConfigSecretFileProvider.kt) class.
An instance is created with a collection of files that contain secrets.
Each file is expected to contain key-value pairs separated by newline characters.
Empty lines or lines starting with a hash character ('\#') are ignored.
So the format is close to typical properties files, e.g.:

```
# Database credentials
dbUser=scott
dbPassword=tiger

# Messaging credentials
rabbitMqUser=xxxx
rabbitMqPassword=yyyy
```

When receiving a query for a specific secret, the provider implementation reads the files in the order they have been provided line by line until it finds a key that matches the requested secret path.
It then returns the corresponding value.
If all files have been processed without finding a matching key, the provider throws an exception.
This implementation has the following consequences:

- Since no caching is performed, changes on secret files (via an external mechanism) are directly visible the next time the value of a secret is queried.

- If multiple secret files are configured, secrets in one file can override the values of secrets in other files.
  The values with the highest priority just have to be defined in the file that is listed first.
  This is useful, for instance, to support different testing environments.
  It can be achieved by having a file with default secret values; but before this file, another file is placed which overrides selected secrets that are specific for a test environment.

The `ConfigSecretFileProvider` implementation is applicable in different scenarios.
It can be used in a local test setup where files with secrets are stored on the local hard drive; here the absolute paths to the secret files have to be provided.

It can also be used in production with mechanisms that inject secrets as files into containers.
One example of such a mechanism is the [Agent sidecar injector](https://developer.hashicorp.com/vault/docs/platform/k8s/injector) of HashiCorp Vault, which can create volume mounts for Kubernetes pods containing files with secrets.
The content of such files, which secrets to export, and in which format, can be configured using annotations in interested pods.

## Configuration

To activate `ConfigSecretFileProvider`, the application configuration must contain a section `configManager` with a property `secretProvider` set to the value "secret-files".
In addition, there is only one configuration property supported for the list of files with secrets to be consumed by the provider.
Here the full paths must be specified in a comma-delimited list; this is a mandatory property.
The fragment below shows an example:

```
configManager {
  secretProvider = "secret-file"
  configSecretFileList = "/mount/secrets/ort-server-dev, /mount/secrets/ort-server"
}
```

In this example, two files with secrets are configured. The file *ort-server-dev* may contain specific values for a dev environment. Since it is listed first, these values override secrets with the same keys defined in the second file, *ort-server*.

The `configSecretFileList` property can alternatively be set via the `SECRET_FILE_LIST` environment variable.
