# Azure Key Vault

This module provides an implementation of the [Secrets Provider Abstraction](../README.md) that is backed by the [Azure Key Vault](https://azure.microsoft.com/en-us/services/key-vault/).

## Configuration

The secrets provider supports the following configuration options:

### `azureKeyVaultName` (required)

The name of the Azure Key Vault to use.

## Authentication

This secrets provider implementation uses the official Azure SDK for Java.
The SDK supports multiple authentication methods and automatically detects which one is configured in the environment.
See the [documentation](https://learn.microsoft.com/en-us/java/api/overview/azure/identity-readme?view=azure-java-stable#credential-classes) for supported authentication methods.

When deploying ORT Server to [Azure Kubernetes Service (AKS)](https://learn.microsoft.com/en-us/azure/aks/), the recommended way is to use [workload identities](https://learn.microsoft.com/en-us/azure/aks/workload-identity-overview).
