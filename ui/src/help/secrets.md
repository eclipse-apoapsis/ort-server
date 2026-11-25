# Secrets

A secret keeps a confidential value, such as a password, a personal access token or a username,
that a run needs to access an external service. ORT Server keeps the value in the secret storage
of this instance, apart from the other data, and never shows it again: after a secret has been
created, its value can only be replaced.

Other settings refer to a secret by its name instead of containing the value:

- [Infrastructure services](infrastructure-services.md) take the username and the password for
  a URL from two secrets.
- Environment variables in the repository's `.ort.env.yml` file can take their values from
  secrets, with `secretName` instead of `value`.

When credentials are given while adding a repository, ORT Server creates the secrets
`REPOSITORY-USERNAME` and `REPOSITORY-PASSWORD` for the repository.

A secret can be defined for an organization, a product or a repository. A secret that an
infrastructure service still uses cannot be deleted.

[Read more about secrets](https://eclipse-apoapsis.github.io/ort-server/docs/user-guide/concepts/external-services#secrets)

## Name {#name}

The name by which infrastructure services and `.ort.env.yml` files refer to the secret. It may
contain letters, numbers, hyphens and spaces, but not start or end with a space, and it cannot be
changed later.

A run looks up a secret name in its repository first, then in the product and then in the
organization, and uses the first secret it finds. A repository or a product can therefore replace
a secret of its organization with its own secret of the same name, also for the infrastructure
services that the organization defines.

## Value {#value}

The confidential value itself, such as the password or the token. It cannot be read after the
secret has been created; to change it, enter a new value on the edit page of the secret.

For an [infrastructure service](infrastructure-services.md#username-secret-ref) that needs no
username, for example because the password is a personal access token, the value of the username
secret can be anything.
