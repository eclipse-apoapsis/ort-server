# External Services

This document describes how to configure credentials for external services, like source code or artifact repositories.

## Infrastructure Services

External services are always accessed via _Infrastructure Services_.
An Infrastructure Service can be created via API/UI or defined in a project's `.ort.env.yml`. It has the following properties:

- An identifying `name`
- An `url` to the external service
- An optional `description`
- A `username secret reference`
- A `password secret reference`
- Optional `credentials types`

Please note, that you have to set a username secret reference, even if it is not needed for your service because you e.g. use a PAT.

### Credential Types

- `Git credentials` should be used, if a service is a Git repository; the credentials are then listed in the `.git-credentials` file.
- `Netrc` should only be used in exceptional cases in which an external tool requires the credentials in the `.netrc` file. The only example
- For services representing artifact repositories, it is typically not necessary to set any credential type. They are handled automatically by the standard authentication mechanism implemented in ORT Server.
  we have seen for this so far are some Git repositories that use Git LFS.
- The type No credentials is to be used for public repositories that do not require authentication, such as Maven Central or Conan Central. Here, the credentials of the service are ignored.

The credentials of an Infrastructure Service can be added to the `Netrc` file and the `Git credentials` files. Adding credentials to the `Netrc` file allows access to most external services.

If Git should not be able to obtain the credentials from the `Netrc` file, one can choose to add the credentials to the `Git credentials` file.

In some cases, there could be conflicting services; for instance, if multiple repositories with different credentials are defined on the same repository server.
Therefore, it is also possible to choose not to include the credentials in any files.

### `.ort.env.yml` Example

```yaml
infrastructureServices:
  - name: 'RepositoryService'
    url: 'https://repo.example.org/test/repository.git'
    usernameSecret: 'testUser'
    passwordSecret: 'testPassword1'
```

## Secrets

ORT stores confidential values&mdash;like usernames, passwords, and tokens&mdash;for Infrastructure Services as _Secrets_.
A Secret has three properties:

- An identifying `name`
- A secret `value`
- An optional `description`

A secret value cannot be retrieved after the Secret has been created, only replaced.

## Environment Definitions

For artifact repositories you often need to generate configuration files for package managers&mdash;such as Maven's `settings.xml` or NPM's `.npmrc`.
ORT Server can create these files based on so-called _Environment Definitions_ specified in a `.ort.env.yml`-file that is committed to the repository under analysis.

The map of environment definitions uses the case-insensitive package manager plugin ID as the key.
Depending on the package manager, the environment definitions may have different properties which are specified as generic string pairs.

### `.ort.env.yml` Example

```yaml
environmentDefinitions:
  conan:
    - service: 'RepositoryService'
      name: 'conancenter'
      url: 'https://center.conan.io'
      verifySsl: 'true'
  maven:
    - service: 'RepositoryService'
      id: 'mainRepo'
  npm:
    - service: 'RepositoryService'
      scope: 'external'
      email: 'test@example.org'
      authMode: 'USERNAME_PASSWORD_AUTH'
  nuget:
    - service: 'RepositoryService'
      sourceName: 'nuget.org'
      sourcePath: 'https://api.nuget.org/v3/index.json'
      sourceProtocolVersion: '3'
      authMode: 'PASSWORD'
  yarn:
    - service: 'RepositoryService'
      alwaysAuth: 'true'
      authMode: 'AUTH_IDENT'
```
