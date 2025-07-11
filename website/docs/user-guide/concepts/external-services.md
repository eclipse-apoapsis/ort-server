# External Services

This document describes how to provide external services to ORT runs, like for instance source code or artifact repositories.

## Infrastructure Services

External services are always accessed via _Infrastructure Services_. An Infrastructure Service can be created via API/UI or defined in a project's `.ort.env.yml`. It has the following properties:

- An identifying `name`
- An `url` to the external service
- An optional `description`
- A `username secret reference`
- A `password secret reference`
- Optional `credentials types`

Please note, that you have to set a username secret reference, even if it is not needed for your service because you e.g. use a PAT.

### Credential Types

The credentials of an Infrastructure Service can be added to the `Netrc` file and the `Git credentials` files. Adding credentials to the `Netrc` file allows access to most external services.

If Git should not be able to obtain the credentials from the `Netrc` file, one can choose to add the credentials to the `Git credentials` file.

In some cases, there could be conflicting services; for instance, if multiple repositories with different credentials are defined on the same repository server. Therefore, it is also possible to choose not to include the credentials in any files.

### `.ort.env.yml` Example

```yaml
infrastructureServices:
- name: "RepositoryService"
  url: "https://repo.example.org/test/repository.git"
  usernameSecret: "testUser"
  passwordSecret: "testPassword1"
```


## Secrets

ORT stores confidential values&mdash;like usernames, passwords, and tokens&mdash;for Infrastructure Services as _Secrets_. A Secret has three properties: 

- An identifying `name` 
- A secret `value`
- An optional `description`

A secret value cannot be retrieved after the Secret has been created, only replaced.

## Environment Definitions

For artifact repositories you often need to generate configuration files for package managers&mdash;such as Maven's `settings.xml` or NPM's `.npmrc`. ORT Server creates these files from _Environment Definitions_ specified in your `.ort.env.yml`-file.

Depending on the package manager, the environment definitions have different properties.

### `.ort.env.yml` Examples

#### Conan

```yaml
environmentDefinitions:
  conan:
  - service: "RepositoryService"
    name: "conancenter"
    url: "https://center.conan.io"
    verifySsl: "true"
```

#### Maven

```yaml
  maven:
    - service: "RepositoryService"
      id: "mainRepo"
```

#### NPM

```yaml
  npm:
    - service: "RepositoryService"
      scope: "external"
      email: "test@example.org"
      authMode: "USERNAME_PASSWORD_AUTH"
```

#### NuGet

```yaml
  nuget:
    - service: "RepositoryService"
      sourceName: "nuget.org"
      sourcePath: "https://api.nuget.org/v3/index.json"
      sourceProtocolVersion: "3"
      authMode: "PASSWORD"
```

#### Yarn

```yaml
  yarn:
    - service: "RepositoryService"
      alwaysAuth: "true"
      authMode: "AUTH_IDENT"
```