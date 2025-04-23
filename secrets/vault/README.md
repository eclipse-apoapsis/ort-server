# HashiCorp Vault Secret Provider Implementation

This module provides an implementation of the [Secrets Abstraction Layer](../README.md) based on [HashiCorp Vault](https://www.vaultproject.io/).

## Synopsis

The [VaultSecretsProvider](src/main/kotlin/VaultSecretsProvider.kt) class implemented here communicates with the REST API of HashiCorp Vault to access secrets managed by this service.

The interaction with the Vault service is done via the [KV Secrets Engine Version 2](https://developer.hashicorp.com/vault/api-docs/secret/kv/kv-v2), which means that versioning of secrets is available.

For authentication, the [AppRole](https://developer.hashicorp.com/vault/api-docs/auth/approle) authentication method is used.
The ORT Server application must be assigned a role with a policy that grants the required access rights to the secrets to be managed.
The ID of this role and a corresponding *secret ID* must be provided as credentials.
Based on this, the provider implementation can obtain an access token from the Vault service.
Refer to the [AppRole Pull Authentication Tutorial](https://developer.hashicorp.com/vault/tutorials/auth-methods/approle) for further details.

The Secrets Abstraction Layer operates on plain keys for secrets and does not support any hierarchical relations between keys.
To map those keys to specific paths in Vault, the provider implementation can be configured with a *root path* that is simply prefixed to the passed in paths for accessing secrets.
Via this mechanism, it is possible for instance that different provider instances (e.g., for production or test) access different parts of the Vault storage.

Another difference between the abstraction layer and Vault is that secrets in Vault can have an arbitrary number of key value pairs stored under the secret’s path, while the abstraction layer assigns only a single value to the secret.
This implementation handles this by using a default key internally.
So, when writing a secret, `VaultSecretsProvider` actually writes a secret at the path specified that has a specific key with the given value.
Analogously, this default key is read when querying the value of a secret.
This speciality has to be taken into account when creating or updating secrets directly in Vault that should be accessible by the Vault abstraction implementation.

## Configuration

As defined by the Secrets SPI module, the configuration takes place in a section named `secretsProvider`.
Here a number of Vault-specific properties can be set as shown by the fragment below.
The service URI and the credentials are mandatory.

This example shows the configuration of the Vault secrets provider:

```
secretsProvider {
  name = "vault"
  vaultUri = "https://vault-service-uri.io"
  vaultRoleId = "<ID of the role assigned to the application>"
  vaultSecretId = "<secret ID>"
  vaultRootPath = "path/to/my/secrets"
  vaultPrefix = "customPrefix"
  vaultNamespace = "custom/namespace"
}
```

This table contains a description of the supported configuration properties:

| Property       | Variable          | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | Default      | Secret |
|----------------|-------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------|--------|
| vaultUri       | VAULT\_URI        | The URI under which the Vault service can be reached. Here only the part up to the host name and optional port is expected; no further URL paths.                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | mandatory    | no     |
| vaultRoleId    | VAULT\_ROLE\_ID   | The implementation uses the [AppRole](https://developer.hashicorp.com/vault/docs/auth/approle) authentication method. With this property the ID of the configured role is specified.                                                                                                                                                                                                                                                                                                                                                                                                                                     | mandatory    | yes    |
| vaultSecretId  | VAULT\_SECRET\_ID | The secret ID required for the [AppRole](https://developer.hashicorp.com/vault/docs/auth/approle) authentication method.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | mandatory    | yes    |
| vaultRootPath  | VAULT\_ROOT\_PATH | Allows configuring a root path that is prepended to the paths provided to the secrets provider. Using this mechanism makes it possible to store the managed secrets under a specific subtree of Vault’s hierarchical structure.                                                                                                                                                                                                                                                                                                                                                                                          | empty string | no     |
| vaultPrefix    | VAULT\_PREFIX     | The different secret engines supported by Vault are mapped to specific paths that need to be specified in API requests. There are default paths, but Vault allows custom configurations for secret engines leading to different paths. In an environment with such a custom configuration, this property can be set accordingly. In case of the KV secret engine, version 2, that is supported by this implementation, the default path for requests is `/v1/secret/data/<path>`. By setting the `vaultPrefix` to something different, e.g. `very-secret`, the URL in requests changes to `/v1/very-secret/data/<path>`. | "secret"     | no     |
| vaultNamespace | VAULT\_NAMESPACE  | The Enterprise version of HashiCorp Vault supports the [namespaces](https://developer.hashicorp.com/vault/docs/enterprise/namespaces) feature. It allows the clear separation of secrets from multiple tenants. If namespaces are enabled, requests to the Vault API must contain a specific header to select the current namespace. If this property is defined, the corresponding header is added.                                                                                                                                                                                                                     | **null**     | no     |

Since the role ID and the secret ID are actually credentials to access the Vault service, they are obtained as secrets from the [ConfigManager](../../config/README.md) under the same keys as listed in the table above.
