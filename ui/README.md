# Web UI

This directory contains the web UI for ORT Server.

## Architecture

The UI is a [React](https://react.dev/) application and uses [Vite](https://vitejs.dev/) as the build tool and [pnpm](https://pnpm.io/) as the package manager.

## Prerequisites

By default, the UI expects ORT Server to be running locally.

If there are no local changes, the fastest way to get started is to use published ORT Server images for UI development.
To do so, run the following commands from the project root directory:

```shell
$ docker compose pull # Ensure that all images are up-to-date.
$ docker compose up -d # Bring services up in detached mode.
```

If you depend on local changes to the Kotlin backend, you instead need to build the images locally:

```shell
$ ./gradlew :buildAllImages # Build all images locally.
$ docker compose up -d # Bring services up in detached mode.
```

Next, ensure that the API definitions are up to date by running:

```shell
$ ./gradlew :core:generateOpenApiSpec
$ pnpm -C ui install
$ pnpm -C ui build
```

## Development

For interactive UI development with live-preview in the browser follow these steps:

1. Run `pnpm -C ui dev`.
2. Ctrl-click the shown `http://localhost:5173/` link.
3. Log in via Keycloak (use "admin" / "admin" as username / password).

### API Changes

If changes to the API were done during development, these are the minimum commands to rerun to reflect the changes (again from the project root):

```shell
$ ./gradlew -PdockerImagePrefix=ghcr.io/eclipse-apoapsis/ -PdockerImageTag=main :core:tinyJibDocker
$ docker compose up -d core
$ ./gradlew :core:generateOpenApiSpec
$ pnpm -C ui generate:api
```

## e2e tests

To run the Playwright e2e tests locally, first start core service (also starts keycloak, postgres and rabbitmq) and dev UI, then run the tests:

```shell
$ docker compose up -d core
$ cd ui
$ pnpm dev
$ pnpm test:e2e
```

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

| Variable       | Default                               | Description                          |
| -------------- | ------------------------------------- | ------------------------------------ |
| `UI_API_URL`   | `http://localhost:8080`               | The URL of the ORT Server API.       |
| `UI_URL`       | `http://localhost:8082`               | The URL of the UI.                   |
| `UI_BASEPATH`  | `/`                                   | The base path of the UI.             |
| `UI_AUTHORITY` | `http://localhost:8081/realms/master` | The URL of the Keycloak realm.       |
| `UI_CLIENT_ID` | `ort-server-ui`                       | The client ID of the UI in Keycloak. |
