# Docker Compose

The easiest way to run the ORT Server for testing is to use [Docker Compose](https://docs.docker.com/compose/).
For a proper deployment to Kubernetes, the project will later provide a Helm chart.

:::warning
Do not use the Docker Compose setup in production as it uses multiple insecure defaults, like providing Keycloak without TLS.
:::

To use Docker Compose, you need to have [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/) installed.
Also, you have to clone the ORT Server repository:

```shell
git clone https://github.com/eclipse-apoapsis/ort-server.git
```

## Run with Published Images

To start the ORT Server with the required third-party services, run:

```shell
docker compose up
```

This will use the ORT Server images [published on GitHub](https://github.com/orgs/eclipse-apoapsis/packages?ecosystem=container).
By default, the `main` tag is used.
Those images are built from the `main` branch of the repository.
To use a different tag, you can set the `ORT_SERVER_IMAGE_TAG` environment variable, for example:

```shell
ORT_SERVER_IMAGE_TAG=0.1.0-RC1 docker compose up
```

By default, the ORT Server API is exposed on port 8080.
If this port is already in use, it can be changed using the `ORT_SERVER_CORE_PORT` environment variable:

```shell
ORT_SERVER_CORE_PORT=8090 docker compose up
```

When using a different port, please make sure that it is not used by [another service](#access-the-services).

### Update the Images

Docker Compose will not automatically update the images for a specific tag like `main` once they have been pulled.
To update the images to the latest version, run:

```shell
docker compose pull
```

## Run with Local Images

First, ensure to have [Docker BuildKit](https://docs.docker.com/build/buildkit/) enabled by either using Docker version
23.0 or newer, running `export DOCKER_BUILDKIT=1`, or configuring `/etc/docker/daemon.json` with:

```json
{
  "features": {
    "buildkit": true
  }
}
```

Then you can build the images with the following command:

```shell
./gradlew buildAllImages
```

This will build the images with the `latest` tag.
Then you can start Docker Compose using the local images by setting the image prefix to an empty string and the tag to `latest`:

```shell
ORT_SERVER_IMAGE_PREFIX= ORT_SERVER_IMAGE_TAG=latest docker compose up
```

### Build specific images

There are also Gradle tasks to only build specific images.
For example, to build only the analyzer worker image, run:

```shell
./gradlew buildAnalyzerWorkerImage
```

To list all available tasks, run:

```shell
./gradlew tasks --group=docker
```

## Access the Services

The following services are exposed by Docker Compose:

| Service        | URL                              | Credentials                                             |
| -------------- | -------------------------------- | ------------------------------------------------------- |
| ORT Server API | http://localhost:8080/swagger-ui |                                                         |
| ORT Server UI  | http://localhost:8082            | Same as Keycloak                                        |
| Keycloak       | http://localhost:8081            | Administrator: admin:admin<br/>User: ort-admin:password |
| PostgreSQL     | http://localhost:5433            | postgres:postgres                                       |
| RabbitMQ       | http://localhost:15672           | admin:admin                                             |
| Graphite       | http://localhost:8888            | root:root                                               |
| Grafana        | http://localhost:3200            | admin:1234                                              |

## Development

### HTTP request collections

When using IntelliJ IDEA Ultimate, you can use the [integrated HTTP client](https://www.jetbrains.com/help/idea/http-client-in-product-code-editor.html) to execute requests against the ORT Server.
The requests can be found in [scripts/requests](https://github.com/eclipse-apoapsis/ort-server/tree/main/scripts/requests).

### Debugging

To debug the ORT Server in IntelliJ, you can use a composition with only some selected services:

```shell
docker compose up rabbitmq keycloak
```

Please note that `postgres` does not have to be explicitly passed: since it is a dependency of Keycloak, it will be started automatically.

Then execute the ORT Server in IntelliJ with the run configuration ["Run ORT Server"](https://github.com/eclipse-apoapsis/ort-server/blob/main/.run/Run%20ORT%20Server.run.xml).

## Troubleshooting

### Flyway migration errors

When starting the ORT Server, you can run into the following error:

> Exception in thread "main" org.flywaydb.core.api.exception.FlywayValidateException: Validate failed: Migrations have failed validation

This usually happens when you previously started the ORT Server with a different version of the database schema than the one you are currently using.
To solve this issue, you need to investigate which migrations failed and then manually fix the database schema.
During testing, this is often not worth the effort, so you can also clean the database and start from scratch.
One way to clean the database is to shut down Docker Compose and delete all volumes:

```shell
docker compose down -v
```

### Major version upgrades of PostgreSQL

PostgreSQL does not support reading the data directory of a previous major version.
When upgrading the PostgreSQL version in the Docker Compose setup, the database data directory must be manually migrated by following these steps:

1. Stop the Docker Compose setup:

   ```shell
   docker compose stop
   ```

2. Start only the PostgreSQL service:

   ```shell
   docker compose up -d postgres
   ```

3. Make backups of the ORT Server and Keycloak databases:

   ```shell
   docker compose exec postgres pg_dump -Fc -U postgres -d ort_server -n public > keycloak.dump
   docker compose exec postgres pg_dump -Fc -U postgres -d ort_server -n ort_server > ort-server.dump
   ```

4. Stop Docker Compose and delete the volumes:

   ```shell
   docker compose down -v
   ```

5. Update the PostgreSQL version in `docker-compose.yml`.

6. Start the PostgreSQL service again:

   ```shell
   docker compose up -d postgres
   ```

7. Import the database backups:

   ```shell
   cat keycloak.dump | docker compose exec -T postgres pg_restore -U postgres -d ort_server -n public
   cat ort-server.dump | docker compose exec -T postgres pg_restore -U postgres -d ort_server -n ort_server
   ```

8. Start all services:

   ```shell
   docker compose up -d
   ```
