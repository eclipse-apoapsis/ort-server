# Web UI

This directory contains the web UI for ORT Server.

## Architecture

The UI is a [React](https://react.dev/) application and uses [Vite](https://vitejs.dev/) as the
build tool and [pnpm](https://pnpm.io/) as the package manager.

## Development

The UI expects ORT Server to be running locally.
In addition, the following Gradle task must be executed to generate the OpenAPI specification locally:

```shell
./gradlew :core:generateOpenApiSpec
```

## Run the UI

Here are the instructions to start the UI in local development mode:

1. Go to the `ui` folder.
2. Run `pnpm install` followed by `pnpm dev`.
3. Ctrl-click the shown `http://localhost:5173/` link.
4. Log in via Keycloak (use "admin" / "admin" as username / password).

## Generating the UI Query Client

As a precondition for generating the query client the OpenAPI specification must be generated as documented in
[Development](#development).

The query client is generated automatically as part of `pnpm build`.
To generate it manually, for example for testing local changes to the API, run `pnpm generate:api`.

## Docker

The Docker image for the UI is built as part of the `buildAllImages` Gradle task.
To build it manually, run the following steps from the root of the repository:

- Build the OpenAPI specification: `./gradlew :core:generateOpenApiSpec`
- Build the Docker image: `docker build -t ort-server-ui -f ui/docker/UI.Dockerfile ui`

To run the Docker image, use the following command:

```shell
docker run --rm -p 8082:80 ort-server-ui
```

The Docker image can be configured by the following environment variables:

| Variable              | Default                               | Description                           |
| --------------------- | ------------------------------------- | ------------------------------------- |
| `UI_API_URL`          | `http://localhost:8080`               | The URL of the ORT Server API.        |
| `UI_URL`              | `http://localhost:8082`               | The URL of the UI.                    |
| `UI_BASEPATH`         | `/`                                   | The base path of the UI.              |
| `UI_AUTHORITY`        | `http://localhost:8081/realms/master` | The URL of the Keycloak realm.        |
| `UI_CLIENT_ID`        | `ort-server-ui`                       | The client ID of the UI in Keycloak.  |
| `UI_CLIENT_ID_SERVER` | `ort-server`                          | The client ID of the API in Keycloak. |
