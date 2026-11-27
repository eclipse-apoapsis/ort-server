# Secrets

A secret stores a confidential value, such as a password, a personal access token or a username,
that a run needs to access an external service. For security reasons, the value of a secret is
never displayed after the secret has been created. You can, however, replace it with a new value
at any time.

Other settings refer to a secret by its name, so the confidential value never appears in them:

- [Infrastructure services](infrastructure-services.md) take the username and the password for
  accessing a service from two secrets.
- Environment variables in the `.ort.env.yml` file of a repository can take their values from
  secrets. To do so, specify `secretName` instead of `value`.

If you enter credentials when adding a repository, the secrets `REPOSITORY-USERNAME` and
`REPOSITORY-PASSWORD` are created for the repository automatically.

You can define secrets for an organization, a product or a repository. A secret cannot be deleted
while an infrastructure service still uses it.

[Read more about secrets](https://eclipse-apoapsis.github.io/ort-server/docs/user-guide/concepts/external-services#secrets)

## Name {#name}

The name that infrastructure services and `.ort.env.yml` files use to refer to the secret. The
name can contain letters, numbers, hyphens and spaces, but cannot start or end with a space. It
cannot be changed after the secret has been created.

When a run needs a secret, it searches for the name in the repository first, then in the product,
and finally in the organization, and uses the first match. This lets a repository or a product
override a secret of its organization by defining a secret with the same name. The override also
applies to infrastructure services defined for the organization.

## Value {#value}

The confidential value, such as a password or a token. The value cannot be displayed after the
secret has been created. To change it, enter a new value on the edit page of the secret.

If an [infrastructure service](infrastructure-services.md#username-secret-ref) does not need a
username, for example because it uses a personal access token, the username secret can have any
value.
