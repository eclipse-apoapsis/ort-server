# Azure Service Bus

This module provides an implementation of the [Transport Abstraction](./) that is backed by the [Azure Service Bus](https://azure.microsoft.com/en-us/products/service-bus/).

## Configuration

The storage supports the following configuration options:

### `namespace`

The name of the [Azure Service Bus namespace](https://learn.microsoft.com/en-us/azure/service-bus-messaging/service-bus-messaging-overview#namespaces).

### `queueName`

The name of the queue to use for sending and receiving messages.

## Authentication

This storage implementation uses the official Azure SDK for Java.
The SDK supports multiple authentication methods and automatically detects which one is configured in the environment.
See the [documentation](https://learn.microsoft.com/en-us/java/api/overview/azure/identity-readme?view=azure-java-stable#credential-classes) for supported authentication methods.

When deploying ORT Server to [Azure Kubernetes Service (AKS)](https://learn.microsoft.com/en-us/azure/aks/), the recommended way is to use [workload identities](https://learn.microsoft.com/en-us/azure/aks/workload-identity-overview).
