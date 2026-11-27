# Infrastructure Services

An infrastructure service is an external service that a run needs to access, such as a source code
repository or an artifact repository from which dependencies are downloaded. It specifies which
credentials to use for which URL. Whenever a run accesses a URL that matches the service, it uses
the credentials of that service.

You can define a service for an organization, a product or a repository. If several repositories
share the same credentials, define the service once for their product or organization.
Repositories can also declare services in their `.ort.env.yml` file.

The username and password of a service are not entered directly. Instead, the service refers to
[secrets](secrets.md) that contain them. Create the secrets first on the **Secrets** page of the
organization, product or repository.

[Read more about external services](https://eclipse-apoapsis.github.io/ort-server/docs/user-guide/concepts/external-services)

## URL Prefix {#url}

The URL of the server, or of a part of the server, that the credentials apply to. When a run needs
credentials for a URL, only services with the same host name are considered:

- If the URLs of one or more services are a prefix of the requested URL, the service with the
  longest matching URL is used. This lets you define credentials for an entire server and
  different credentials for specific parts of it.
- Otherwise, the service whose URL is most similar to the requested URL is used.

Note that a path in the URL does not restrict the credentials to that path. For example, if
`https://repo.example.org/team-a` is the only service for `repo.example.org`, its credentials are
also used for `https://repo.example.org/team-b/project.git`.

## Username Secret {#username-secret-ref}

The secret that contains the username for accessing the service. Select the
[name of an existing secret](secrets.md#name), not the username itself.

A username secret is required even if the service does not need a username, for example when the
password is a personal access token. In this case, use a secret with any value.

## Password Secret {#password-secret-ref}

The secret that contains the password or personal access token for accessing the service. Select
the [name of an existing secret](secrets.md#name), not the password itself.

## Add Credentials To Files {#credentials-types}

Runs always use the credentials of the service when accessing its URL directly. However, some
tools used during a run can only read credentials from their own configuration files. Select the
files that the credentials should be added to:

- **Git Credentials File**: Used by Git. Select this option for services that are Git
  repositories.
- **Netrc File**: Used by many other command line tools. This option is rarely needed, for example
  for Git repositories that use Git LFS.

Artifact repositories, such as Maven or npm registries, usually require neither file.

You can also leave both options unselected. This is useful when several services with different
credentials are defined for the same server: the Netrc file can contain only one set of credentials
per server, so add only one of these services to it.

[Read more about credentials types](https://eclipse-apoapsis.github.io/ort-server/docs/user-guide/concepts/external-services#credential-types)
