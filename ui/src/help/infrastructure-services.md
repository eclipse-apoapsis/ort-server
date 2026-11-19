# Infrastructure Services

An infrastructure service is an external service that a run needs to access, such as a source
code repository or an artifact repository that dependencies are downloaded from. It tells ORT
Server which credentials to use for which URL. During a run, ORT Server uses the credentials of a
service whenever it or one of the tools it runs accesses a URL that the service matches.

A service can be defined for an organization, a product or a repository. If several repositories
share the same credentials, define the service for their product or organization. Repositories
can also declare services in their `.ort.env.yml` file.

The username and the password of a service are not entered here but kept in secrets, so that the
confidential values stay out of the service definition. A secret has to exist before a service
can use it; create it on the **Secrets** page of the organization, the product or the repository.
The value of a secret cannot be read after it has been created, only replaced.

[Read more about external services](https://eclipse-apoapsis.github.io/ort-server/docs/user-guide/concepts/external-services)

## URL Prefix {#url}

The URL of the server, or of the part of it, that the credentials of the service are for. When a
run needs credentials for a URL, ORT Server considers the services whose URL has the same host:

- If the URLs of some of them are a prefix of the requested URL, the service with the longest URL
  wins. This allows defining credentials for a whole server and different ones for a part of it.
- If none of them is a prefix, ORT Server still uses the only service of the host, or, if the
  host has several services, the one whose URL is most similar to the requested URL.

The path of the URL therefore does not keep the credentials away from other paths on the same
server. For example, a service with the URL `https://repo.example.org/team-a` is also used for
`https://repo.example.org/team-b/project.git` if it is the only service for
`repo.example.org`.

## Username Secret {#username-secret-ref}

The secret that holds the username for accessing the service. Select the name of an existing
secret, not the username itself.

A username is required even when the service does not need one, for example when the password is
a personal access token. In that case, use a secret with any value.

## Password Secret {#password-secret-ref}

The secret that holds the password or the personal access token for accessing the service.
Select the name of an existing secret, not the password itself.

## Add Credentials To Files {#credentials-types}

ORT Server always uses the credentials of the service when it accesses the service's URL itself.
Some external tools, however, only find credentials in a configuration file. Choose the files the
credentials should be written to:

- **Git Credentials File**: the `.git-credentials` file, read by the Git command line tool.
  Choose it for services that are Git repositories.
- **Netrc File**: the `.netrc` file, read by many other command line tools. It is rarely needed;
  one example is Git repositories that use Git LFS.

Services for artifact repositories, such as Maven or npm registries, usually need neither file.

Choosing no file at all is also valid. This helps when several services with different
credentials are defined for the same server: the `.netrc` file holds only one set of credentials
per server, so only one of these services should be added to it.

[Read more about credentials types](https://eclipse-apoapsis.github.io/ort-server/docs/user-guide/concepts/external-services#credential-types)
