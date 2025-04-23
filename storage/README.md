# Storage Abstraction

This document describes the storage abstraction used within the ORT Server to provide a generic mechanism to store arbitrary data temporarily or for a longer time.

## Purpose

Some components of ORT Server have the requirement to store (potentially large) data for different purposes.
One prominent example is storing the reports produced by the reporter.
They need to be persisted somewhere for a certain amount of time, so that they can be queried and accessed by users.
This use case requires a permanent storage for files, but there are other use cases as well where some data needs to be cached temporarily, e.g. storing license texts obtained by the scanner, so that they can be referenced in reports later, or caching metadata of packages for some package managers, so that it does not have to be retrieved on each analyzer run.

These use cases have in common that arbitrary data has to be stored under a specific key.
A suitable abstraction would therefore be a generic storage interface oriented on a key/value storage.
With regard to potential implementations, there are different options that depend on the concrete storage characteristics of specific data, such as

- Which data needs to be stored?
- How big is it?
- How long does the data need to be stored (short-term caching vs. long-term persistence)?

Bringing this together, there can be a generic, key-value-based storage interface defining operations to access and manipulate data on a storage.
For this interface, different implementations exist.
This is in-line with other abstraction layer implementations used within ORT Server, e.g., for storing secrets or passing messages.
It allows integrating various storage mechanisms supported by the platform on which the ORT Server application is running.
In addition, a single application instance can be configured to use multiple storage implementations for different kinds of data, so that a high flexibility can be achieved.

## Service Provider Interfaces

This section describes the interfaces that need to be implemented to integrate a concrete storage product.

### Access Interface

The storage abstraction layer defines a basic interface, [StorageProvider](spi/src/main/kotlin/StorageProvider.kt), allowing to associate data with (string-based) keys.
The data is represented by streams, taking the fact into account that it can become potentially large.
(For instance, for the use case of storing report files, some of the files will have sizes with more than 10 MB.)
So, it can be accessed without loading it completely into memory.
The interface defines only a minimum set of CRUD operations to simplify its implementation.

A dedicated value class has been introduced to represent storage keys.
The function to query data returns a `StorageEntry` object which contains the `InputStream` with the actual data and a string with its content-type.
The idea behind this is that the ORT Server API may in some cases provide direct access to stored data, as is the case for serving report files.
The corresponding endpoint can then easily set the content-type header accordingly.
A future extension could be adding support for other/arbitrary metadata properties.

The `StorageProvider` interface defines the `write` operation to create and update entries in the storage.
It expects the key and the stream with the data to store.
Additionally, the (optional) content-type can be provided.
Another mandatory parameter is the length of the data.
This information is required by some storage products, for instance by Azure Storage Accounts.
Since the size of the data cannot be obtained easily from the passed in stream, it has to be provided explicitly.

The interface defines further operations to remove an entry from the storage and to check whether an entry with a given key exists.
For the time being, there is no operation to list all existing keys.
The expectation is that each use case implemented with a storage defines a specific convention how to construct keys and how they are supposed to be interpreted.

There are a few further assumptions taken by the abstraction layer implementation to simplify concrete implementations of the `StorageProvider` interface:

- A concrete implementation can throw arbitrary, proprietary exceptions.
  These are caught by the abstraction and wrapped into a standard `StorageException` instance.
- The `read()` operation should throw an exception when the passed in key does not exist.
  Clients should use the `contains()` operation to make sure that the desired data is actually available.

### Factory Interface

As is typical for the abstraction layers used within ORT Server, the storage abstraction defines a factory interface to create a specific `StorageProvider` instance based on the application configuration:
[StorageProviderFactory](spi/src/main/kotlin/StorageProviderFactory.kt).

The interface defines a `name` property, which is used to look up a specific factory from the classpath - as usual, the available factories are obtained via the Java ServiceLoader mechanism, and the selected one is matched by its name.

The factory function expects a configuration object as parameter.
A concrete implementation can define and evaluate custom configuration options which are accessible from this object.

## Using a Storage

With the [Storage](spi/src/main/kotlin/Storage.kt) class, the storage abstraction defines a facade class that handles the creation and initialization of a concrete `StorageProvider` instance and simplifies the usage of the storage API by providing a number of convenience functions.
To access a storage for a specific use case, a `Storage` instance has to be created using the `create()` function from the companion object.
The function expects an object with the current application configuration and a string defining the current use case.
As mentioned earlier, different storage implementations can be configured for different data to be stored.
To resolve the desired implementation for the current use case, the `create()` function searches for a configuration section under the given identifier.
This section must at least contain a `name` property referencing the `StorageProviderFactory` of the selected implementation.
Further, implementation-specific properties can be present in this section which the factory can evaluate.
With this information, `create()` can do the usual lookup and instantiate the correct `StorageProvider`.

To give a concrete example, we assume that a storage for report files should be configured.
The configuration could look as follows:

```
reportStorage {
  name = database
  namespace = reports
}
```

This fragment basically tells that the report storage is provided by an implementation with the name *database*.
The `namespace` property is evaluated by this implementation.
Given this configuration, a `Storage` object for storing report files can now be obtained in the following way:

``` kotlin
val config = ConfigFactory.load()

val reportStorage = Storage.create("reportStorage", config)
```

The `Storage` class provides functionality that simplifies dealing with data that can be held in memory in form of strings or byte arrays.
Such objects can be read and written directly without having to deal with streams.
It is also responsible for catching all proprietary exceptions thrown by a `StorageProvider` implementation and wrapping them inside `StorageException` objects.
