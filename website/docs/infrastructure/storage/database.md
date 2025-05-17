# Database

This module provides an implementation of the [Storage Abstraction](./) that is backed by a database table.
Arbitrary data is stored using the [Large Objects](https://jdbc.postgresql.org/documentation/binary-data/) mechanism of PostgreSQL.

## Synopsis

This implementation of the Storage Abstraction does not require any external services, but makes use of the ORT Server database to store data.
It accesses the default database provided by Exposed; therefore, it does not require a dedicated database configuration.

One goal of this implementation is to support data that is potentially large and should therefore not be loaded into memory.
With a PostgreSQL database, this is currently only possible by using large objects.
Here the storage table only contains a reference to a large object (a long object ID) holding the actual data.
The object is stored separately and needs to be accessed and manipulated by a PostgreSQL-specific API.

> [!NOTE]
> PostgreSQL also supports the `bytea` datatype, which is easier to use; but this type is only suitable for data of limited size because the full data is always read into memory.

One restriction of the large objects mechanism is that all access to data is only possible within an active transaction.
This does not fit well to the `StorageProvider` interface, which hands over a stream to the client which is consumed later - at that time, the transaction is already gone.
This implementation solves this problem in the following way:

- Small data (whose size is below a configurable threshold) is loaded into memory and passed to the client as a `ByteArrayInputStream`.
- Larger data is copied from the database into a temporary file.
  Then the provider hands over a special stream to the client that deletes the file when it gets closed.
  Thus, the data is accessible outside the transaction.
  (But care should be taken that the stream is always closed.)

Other than that, the implementation is a rather straight-forward mapping from the `StorageProvider` interface to a database table.
The table can be shared between different storage instances storing different kinds of data.
To make this possible, it contains a discriminator column named `namespace`.
The namespace to use must be specified in the configuration.

## Configuration

When creating a `StorageProvider` instance via the `Storage` class the _storage type_ must be provided.
This allows using different kinds of storages for different data in ORT Server.
The provider-specific configuration is then expected in a configuration section whose name matches the storage type.

The following fragment shows an example configuration for the database storage provider.
It assumes that the provider is used for storing reports; so the provider-specific configuration is located below the `reports` element:

```
reports {
  name = "database"
  namespace = "reports"
  inMemoryLimit = 65536
}
```

The `name` property selects the provider implementation.
It must be set to _database_ to select the database storage provider implementation.
The other properties are specific to this implementation and are explained in the table below:

| Property      | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| namespace     | Defines the namespace to be used for the data stored by this provider instance. This allows distinguishing different kinds of data that are all managed by the database storage implementation. This is somewhat redundant to the _storage type_ which determines the configuration section. Since this property is not available in the configuration passed to the storage provider implementation, a dedicated property is needed. Its value must be unique for all database storage provider instances in use. |
| inMemoryLimit | Defines the size of data (in bytes) that can be loaded into memory. Data that exceeds this size is buffered in a temporary file when it is accessed.                                                                                                                                                                                                                                                                                                                                                               |
