# Access to Secrets

This document describes the secrets storage abstraction used within the ORT server to allow the integration with
different secret storage products.

## Purpose

To access source code and artifact repositories for doing analysis runs, the ORT server must have correct credentials.
The infrastructure to be accessed is defined dynamically by users - by setting up the hierarchical structures for organizations, products, and repositories.
While doing this, the corresponding credentials must be provided as well.
This implies that an API is available to create, read, modify, and update secrets or credentials.
With such an API in place, users are enabled to fully manage the credentials required for their infrastructure
themselves - without needing support from server administrators.

> [!NOTE]
> This document treats the terms *secrets* and *credentials* as synonyms.

This means that the ORT server needs to store secrets on behalf of its users.
There is, however, a difference between secrets and other entities managed by users:
Secrets have to be kept strictly confidential.
To achieve this, they are typically stored in dedicated secret storages and not in the database like other data.

Analogously to the [Transport layer abstraction](../transport/README.md), the ORT server should not set on a specific secret storage product, but be agnostic to the environment it is running on.
To support arbitrary products, again, an abstraction for a secret storage service has to be defined.

## Service Provider Interfaces

This section describes the interfaces that need to be implemented to integrate a concrete secret storage product.

### Access Interface

The secrets abstraction layer defines a basic interface, [SecretsProvider](spi/src/main/kotlin/SecretsProvider.kt), with CRUD operations on secrets.
This interface has to be implemented to integrate a concrete storage product.
To simplify potential implementations, the interface is reduced to a bare minimum and just offers functions for the basic use cases:

- read secrets
- write secrets (create new ones or update existing ones)
- remove secrets
- list available secrets

Secrets are identified by paths which are basically strings.
This is the least common denominator over various concrete secret storage products.
While some of them (e.g. [HashiCorp Vault](https://www.vaultproject.io/)) support a hierarchical organization of secrets, others are quite restricted in this regard (for instance, [Azure Key Vault](https://azure.microsoft.com/en-us/products/key-vault) only offers a key-value storage with a limited length of keys).
So, the scope of the secrets storage abstraction lies only in storing the secret value under an arbitrary (maybe even synthetic) key.
Additional metadata that will be required to actually use the secret - such as a human-readable name, a description, or the information to which organization/product/repository it is assigned - need to be stored separately.

There are a few further assumptions taken by the abstraction layer implementation to simplify concrete implementations of the `SecretsProvider` interface:

- When querying a secret for a non-existing path, an implementation should return **null**.
  This result can be interpreted by the abstraction, and a concrete implementation does not need to bother with throwing specific exceptions.

- A concrete implementation can throw arbitrary, proprietary exceptions.
  These are caught by the abstraction and wrapped into a standard exception class.

### Factory Interface

The creation of a concrete `SecretsProvider` instance lies in the responsibility of a factory defined by the [SecretsProviderFactory](spi/src/main/kotlin/SecretsProviderFactory.kt) interface.

The factories available are looked up via the Java ServiceLoader mechanism.
Each factory is assigned a unique name by which they can be identified in the application configuration; thus it can be configured easily which secrets storage implementation to be used.

There is one factory method that expects a configuration object and returns a `SecretsProvider` instance.
The idea here is that the properties required by a specific implementation can also be set in the application configuration; they are then passed through to the factory, which can initialize the provider instance accordingly.

## Using Secrets

Using the `SecretsProvider` interface directly would be rather inconvenient, due to its limited functionality and the implicit assumptions described in the previous section.
Therefore, the abstraction offers a different entry point in form of the [SecretStorage](spi/src/main/kotlin/SecretStorage.kt) class.

`SecretStorage` is first a factory for creating and initializing a concrete `SecretsProvider` implementation.
For this purpose, it offers a `createStorage()` function in its companion object. The function does the following:

- It reads the name of the secret storage implementation to be used from the application configuration.
- It uses a *service loader* to obtain all the registered `SecretsProviderFactory` implementations available on the classpath.
- It searches for the factory implementation with the configured name (and fails if it cannot be found).
- It invokes the factory function of this factory implementation passing in the application configuration to obtain a `SecretsProvider` instance.
- It returns a new `SecretStorage` implementation that wraps this provider instance.

The secrets abstraction consumes a section named `secretsProvider` from the application configuration.
It has the following structure:

```
secretsProvider {
  name = <secret storage implementation name>

  # Properties specific to the selected secret storage implementation
  ...
}
```

A `SecretStorage` instance then allows convenient interaction with the wrapped `SecretsProvider`.
It offers a richer interface for operations on secrets.
Basically, it adds the following functionality on top of that provided by `SecretsProvider`:

- Provider-specific exceptions are caught and wrapped in generic `SecretStorageException` objects.
  So, client code only has to handle this exception type.
- For all operations, there are variants returning a Kotlin `Result` instance instead of throwing an exception.
  They can be used if a more functional style for exception handling is preferred.
  In case of a failure, the `Result` also contains a `SecretStorageException` that wraps the original exception from the underlying provider.
- For querying secrets, there are functions that require the secret in question to exist and throw an exception or return a failure `Result` if this is not the case.
