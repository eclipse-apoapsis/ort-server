# Keycloak Configuration

The ORT Server uses [Keycloak](https://www.keycloak.org/) for authentication and for user management.
To ensure that the ORT Server can be used with a Keycloak instance, the following configuration steps are required.

:::info
The authentication flows used in this document are officially supported by the ORT Server.
There is no guarantee that other authentication flows will work correctly, and they are not tested.
If you need support for an authentication flow that is not listed here, please open an [issue](https://github.com/eclipse-apoapsis/ort-server/issues).
:::

## Clients

It is recommended to create the following clients in Keycloak.
While a single client could be used for all purposes, separating clients allows a more flexible and secure configuration.
An example for this configuration can be seen in the [Docker Compose](../getting-started/docker-compose.md) setup.

### ORT Server Backend

This client is used for authentication by the ORT Server backend to access the Keycloak Admin API.
It is recommended to use the "Service account roles" flow in Keycloak which corresponds to the "Client credentials" flow in OAuth2.
This allows the ORT Server to authenticate with Keycloak without the need for a user account.

- _Default client ID:_ `ort-server-backend`
- _Client authentication:_ Enabled
- _Authentication flow:_ Service account roles
- _Client scopes:_ Default
- _Service account roles:_ `default-roles-<realm-name>`, `admin`

Required configuration for the `core` application:

| Environment variable                                  | Description                                                                  |
| ----------------------------------------------------- | ---------------------------------------------------------------------------- |
| `KEYCLOAK_BASE_URL`                                   | The base URL of the Keycloak instance.                                       |
| `KEYCLOAK_REALM`                                      | The Keycloak realm to use.                                                   |
| `KEYCLOAK_CLIENT_ID`                                  | The client ID of this client.                                                |
| `KEYCLOAK_API_USER`                                   | Set to an empty string to use the service account instead of a user account. |
| `KEYCLOAK_API_PASSWORD` (secret `keycloak.apiSecret`) | The client secret of this client.                                            |

Alternatively, the client can be configured to use the "Direct access grants" flow, which corresponds to the "Resource owner password credentials" flow in OAuth2.
This allows the ORT Server to authenticate with Keycloak using a user account, but requires the user to have the `admin` role.
For this, the `KEYCLOAK_API_USER` must be set to the username instead of an empty string, and the `KEYCLOAK_API_PASSWORD` must be set to the password of the user.

### ORT Server API

This client is not used for authentication but for the audience claim in the access tokens issued by Keycloak.
The ORT Server API validates that the audience claim in the access token matches the client ID of this client.

- _Default client ID:_ `ort-server-api`
- _Client authentication:_ Enabled
- _Authentication flow:_ None
- _Client scopes:_ None

Required configuration for the `core` application:

| Environment variable | Description                                                                                                                                                                                  |
| -------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `JWT_ISSUER`         | The expected issuer claim in the access tokens issued by Keycloak. This is usually the base URL of the Keycloak instance followed by `/realms/<realm-name>`.                                 |
| `JWT_URI`            | The URI where Keycloak exposes the public keys for token validation. This is usually the base URL of the Keycloak instance followed by `/realms/<realm-name>/protocol/openid-connect/certs`. |
| `JWT_AUDIENCE`       | The expected audience claim in the access tokens issued by Keycloak. This must be set to the client ID of this client.                                                                       |
| `JWT_REALM`          | The Keycloak realm to use. This is used for token validation and must match the realm used in the `KEYCLOAK_REALM` variable.                                                                 |

#### Client scope

It is recommended to create a client scope `ort-server-api-access` to easily include the correct audience claim in the access tokens issued by Keycloak for other clients.
The client scope needs the following protocol mapper:

- _Mapper type:_ Audience
- _Included client audience:_ `ort-server-api`
- _Add to access token:_ On

### ORT Server UI

This client is used for authentication by the ORT Server UI.
Currently, the UI only supports the "Standard flow" which corresponds to the "Authorization code" flow in OAuth2.

- _Default client ID:_ `ort-server-ui`
- _Client authentication:_ Disabled
- _Authentication flow:_ Standard flow
- _PKCE method:_ S256
- _Client scopes:_ `basic`, `email`, `profile`, `ort-server-api-access`
- _Valid redirect URIs:_ The URL where the UI is hosted followed by `/*`, for example `http://localhost:8082/*`.
- _Web origins:_ Use `+` to allow all valid redirect URIs.

The remaining default scopes are not required and can be removed to minimize the token size.

Required configuration for the `ui` application:

| Environment variable | Description                                                                                                                                        |
| -------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| `UI_AUTHORITY`       | The URL of the Keycloak realm to use for authentication. This is usually the base URL of the Keycloak instance followed by `/realms/<realm-name>`. |
| `UI_CLIENT_ID`       | The client ID of this client.                                                                                                                      |

### ORT Server CLI

This client is used for authentication by the [ORT Server CLI](../../user-guide/cli/getting-started.md).
Currently, the CLI only supports the "Direct access grants" flow which corresponds to the "Resource owner password credentials" flow in OAuth2.

- _Default client ID:_ `ort-server-cli`
- _Client authentication:_ Disabled
- _Authentication flow:_ Direct access grants
- _Client scopes:_ `basic`, `email`, `profile`, `ort-server-api-access`, `offline_access`

The remaining default scopes are not required and can be removed to minimize the token size.

Required configuration for the `core` application:

| Environment variable     | Description                            |
| ------------------------ | -------------------------------------- |
| `CLI_KEYCLOAK_BASE_URL`  | The base URL of the Keycloak instance. |
| `CLI_KEYCLOAK_REALM`     | The Keycloak realm to use.             |
| `CLI_KEYCLOAK_CLIENT_ID` | The client ID of this client.          |

With this configuration, the CLI can auto-detect the necessary information to authenticate with Keycloak via the [API](/api/get-cli-oidc-config).

### Additional clients

If additional applications need to authenticate with Keycloak to access the ORT Server API, it is recommended to create separate clients for each application and include the `ort-server-api-access` client scope to ensure that the correct audience claim is included in the access tokens issued by Keycloak.
For testing purposes, it is also possible to use the `ort-server-cli` client, as it uses the "Direct access grants" flow which allows authentication with a user account.

## Token lifespan

To configure the lifespan of access and refresh tokens, or how long an offline token is valid, see the following sections of the Keycloak documentation:

- [Session and token timeouts](https://www.keycloak.org/docs/latest/server_admin/index.html#_timeouts)
- [Offline access](https://www.keycloak.org/docs/latest/server_admin/index.html#_offline-access)
