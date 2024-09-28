# Azure Blob Storage

This module provides an implementation of the [Storage Abstraction](../README.md) that is backed by the [Azure Blob Storage](https://azure.microsoft.com/en-us/products/storage/blobs/).

## Configuration

The storage supports the following configuration options:

### `azureBlobStorageAccountName`

The name of the [Azure Storage account](https://learn.microsoft.com/en-us/azure/storage/common/storage-account-overview) to use.
This is used to build the endpoint URL for the default Azure blob storage endpoint in the form `https://<azureBlobStorageAccountName>.blob.core.windows.net`. 

This option is mutually exclusive with `azureBlobEndpointUrl`.

### `azureBlobEndpointUrl`

The endpoint URL of the Azure Blob storage.
This must be used when the blob storage is not available at the default URL.
For example, when accessing sovereign clouds (e.g., Azure Government), when using a custom domain, when using a private endpoint, or when using Azure Stack.

This option is mutually exclusive with `azureBlobStorageAccountName`.

### `azureBlobContainerName` (required)

The name of the container to use for storing the blobs.
The container must exist before the storage provider is used.

## Authentication

This storage implementation uses the official Azure SDK for Java.
The SDK supports multiple authentication methods and automatically detects which one is configured in the environment.
See the [documentation](https://learn.microsoft.com/en-us/java/api/overview/azure/identity-readme?view=azure-java-stable#credential-classes) for supported authentication methods.

When deploying ORT Server to [Azure Kubernetes Service (AKS)](https://learn.microsoft.com/en-us/azure/aks/), the recommended way is to use [workload identities](https://learn.microsoft.com/en-us/azure/aks/workload-identity-overview).
