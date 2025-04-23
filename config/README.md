This document describes the configuration abstraction used within the ORT server to allow access to configuration data stored externally.

## Purpose

While simple configuration properties in ORT Server are accessed via the [Typesafe Config library](https://github.com/lightbend/config), there is other configuration information that needs to be treated specially.
Reasons for this could be that the information is confidential (e.g., credentials for accessing specific infrastructure services) and is therefore managed by a secret storage.
Or the size of the configuration data is bigger, so that it is not feasible to inject it via environment variables, but an external storage is needed (maybe a version control system).

According to the philosophy of ORT Server, it should be flexible to be integrated with different mechanisms for accessing such special configuration data.
A concrete runtime environment may offer specific services that are suitable for the use cases at hand, for instance, key vaults for storing credentials.
By implementing the corresponding interfaces, it should be possible to integrate such mechanisms with ORT server.

The use cases addressed by this abstraction, that go beyond simple configuration properties, are the following:

- **Loading files:**
  Especially for the configuration and customization of workers, files are sometimes needed.
  Examples include rule sets for the Evaluator, template files for generating reports, or other special-purpose ORT configuration files.
  Since this data can affect the results produced by ORT runs, it is often desired to keep it under version control, so that changes with undesired effects can be rolled back if necessary.
- **Reading secrets for infrastructure services:**
  This is required when accessing external services from a worker, such as external advisor services or remote scanners.
  Note that this is not related to the secrets ORT Server manages on behalf of the users to access source code or artifact repositories.
  The secrets in this context are managed centrally by the administrators of ORT Server.

## Service Provider Interfaces

This section describes the interfaces that need to be implemented to obtain configuration data from special sources.
There are dedicated interfaces for different use cases that are discussed in their own subsections.

### Access to Configuration Files

One service provider interface deals with loading configuration files from an external storage.
It is used for instance to read template or script files.

#### Access Interface

For dealing with configuration files, the abstraction defines a basic interface, [ConfigFileProvider](spi/src/main/kotlin/ConfigFileProvider.kt).
It defines operations for obtaining the content of a configuration file as a stream, for checking whether a specific file exists, and for listing the configuration files under a specific path.

Paths to configuration files are represented by a special value class named `Path`.
The interpretation of such a path is implementation-specific.
Typically, it will reference some kind of relative path below a root folder.

To uniquely identify a specific version of a configuration file, there is another property involved, the so-called *context*.
In terms of the interface, this is another string-based value class whose meaning depends on a concrete implementation.
The idea behind this property is that there could be multiple sets of configuration files that could change over time (e.g. when they are stored in a version control system) or apply to different (staging) environments.
A concrete implementation can assign a suitable semantic to this string value.
For instance, if configuration data is loaded from a version control system, the context could be interpreted as the revision.
The interface has a `resolveContext()` function that allows transforming a given context value into a normalized or resolved form.
More information about the intention of this function can be found in the [Using Advanced Configuration](#using-advanced-configuration) section.

Regarding error handling, an implementation is free to throw arbitrary exceptions.
They are caught by the wrapper class and rethrown as standard `ConfigException` exceptions.

#### Factory Interface

The factory interface for the configuration file provider abstraction is defined by the [ConfigFileProviderFactory](spi/src/main/kotlin/ConfigFileProviderFactory.kt) interface.
It works analogously to typical factory interfaces for other abstractions used within ORT Server.

This means that instances are loaded via the *service loader* mechanism from the classpath.
The interface defines a `name` property that is used to select a specific factory.
It has a `createProvider()` function to create the actual provider object based on passed in configuration.

### Access to Secrets

Another abstraction allows reading the values of secrets from the configuration that can be used for instance to access external systems ORT Server has to interact with.

#### Access Interface

The service provider interface is defined by the [ConfigSecretProvider](spi/src/main/kotlin/ConfigSecretProvider.kt) interface.
It is quite simple and contains only a single function to query the value of a secret.
The secret in question is identified using the `Path` value type, which is also used by other configuration abstractions.
The function returns the value of the secret as a String.
It should throw an exception if the requested secret does not exist or cannot be resolved.

Other functionality, like contains checks or listing secrets, is not required.
In typical use cases, the secrets to be looked up are directly referenced by their names; so it is sufficient to resolve these names and obtain the corresponding values.
This also simplifies concrete implementations, which could, for instance, look up the values from environment variables, from files, or from a specific storage for secrets.

#### Factory Interface

The factory interface for creating concrete `ConfigSecretProvider` instances has exactly the same structure as the one already discussed for `ConfigFileProvider`.
So, everything mentioned there applies to this interface as well.

## Using Advanced Configuration

As usual, the configuration abstraction provides a facade class that is responsible for loading the configured provider implementations and that simplifies the interactions with them.
This is the [ConfigManager](spi/src/main/kotlin/ConfigManager.kt) class.

Instances are created via the `create()` function of the companion object.
The function expects the application configuration, so that it can determine the provider implementations to load and their specific configuration settings.
In addition, the `create()` function requires a `Context` object as argument.
The context is stored and used for interactions with the `ConfigFileProvider` object.
Hence, it is not necessary to deal with this parameter manually.

But except for convenience, there is another reason for storing the context:
it should remain constant during a whole ORT run to warrant consistency.
Consider the case that configuration data is stored in a version control system.
The context could then reference a branch that contains the configuration files.
This branch may change, however, while an ORT run is in progress, so that a worker executed later may see a different configuration than the one that started earlier.
To address this issue, the `ConfigFileProvider` interface defines a `resolveContext()` function that expects a `Context` argument and returns a normalized or resolved context.
When constructing a `ConfigManager` instance, a flag can be passed whether this function should be called and the resolved context should be stored.
In the example of the version control system, the provider implementation could in its `resolveContext` operation replace a branch name by the corresponding commit ID to pinpoint the configuration files.
To support such constellations, at the beginning of an ORT run, the context should be resolved once and then stored in the database.
Workers started later in the pipeline should obtain it from there.

The interface of the `ConfigManager` class is similar to the ones of the wrapped provider interfaces with a few convenience functions.
The class supports error handling by catching all the exceptions thrown by providers and wrapping them in a standard `ConfigException`.

The configuration passed to the `create()` function must contain a section named `configManager` that at least defines the names of the supported provider implementations to be loaded.
Those are specified by the following properties:

| Property       | Description                                                                                       |
|----------------|---------------------------------------------------------------------------------------------------|
| fileProvider   | The name of the provider factory implementation for creating the `ConfigFileProvider` instance.   |
| secretProvider | The name of the provider factory implementation for creating the `ConfigSecretProvider` instance. |

In addition, the section can then contain further, provider-specific properties.
The following fragment gives an example:

```
configManager {
  fileProvider = gitHub
  fileProviderRepository = ort-server-config
  fileProviderDefaultRevision = main

  secretProvider = env
}
```
