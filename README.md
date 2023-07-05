# ORT Server

The ORT server is a standalone application to deploy the
[OSS Review Toolkit](https://github.com/oss-review-toolkit/ort) as a service in the cloud.

## Local Setup

To start the ORT server with the required 3rd party services, you can use
[Docker Compose](https://docs.docker.com/compose/).

First, build the base images for the analyzer, evaluator, and scanner which contain the external tools:

```shell
cd workers/analyzer/docker
DOCKER_BUILDKIT=1 docker build . -f Analyzer.Dockerfile -t ort-server-analyzer-worker-base-image:latest

cd workers/evaluator/docker
DOCKER_BUILDKIT=1 docker build . -f Evaluator.Dockerfile -t ort-server-evaluator-worker-base-image:latest

cd workers/scanner/docker
DOCKER_BUILDKIT=1 docker build . -f Scanner.Dockerfile -t ort-server-scanner-worker-base-image:latest
```

Then build all Docker images with [Jib](https://github.com/GoogleContainerTools/jib):

```shell
./gradlew jibDockerBuild
```

The `jibDockerBuild` task occasionally gets stuck while pulling the Eclipse Temurin base image. In this case it usually
helps to clean the project and stop the Gradle Daemon:

```shell
./gradlew clean
./gradlew --stop
```

Finally, you can start Docker Compose. Since the choice between ActiveMQ Artemis and RabbitMQ is offered, you need to
choose the one to activate with a [profile](https://docs.docker.com/compose/profiles/):

```shell
docker compose --profile rabbitmq up
```

**Do not use the Docker Compose setup in production as it uses multiple insecure defaults, like providing KeyCloak
without TLS.**

### Accessing the services

| Service        | URL                              | Credentials       |
|----------------|----------------------------------|-------------------|
| ORT Server API | http://localhost:8080/swagger-ui |                   |
| Keycloak       | http://localhost:8081            | admin:admin       |
| PostgreSQL     | http://localhost:5433            | postgres:postgres |
| RabbitMQ       | http://localhost:15672           | admin:admin       |

### Debugging

To debug the ORT server in IntelliJ, you can use a composition with only some selected services:

```shell
docker compose --profile rabbitmq up rabbitmq keycloak 
```

Please note that Postgres does not need to be explicitly passed: since it is a dependency of Keycloak, it will be
automatically started.

Then execute the ORT server in IntelliJ with the run configuration "Run ORT Server".

## Troubleshooting

When starting the ORT Server service you can run into the following error:
`Exception in thread "main" org.flywaydb.core.api.exception.FlywayValidateException: Validate failed: Migrations have failed validation`

You can solve this problem by cleaning the database. Please note that it will empty all ORT Server table.

```shell
docker compose -f docker-compose.yml -f docker-compose-maintenance.yml up flyway
```

## Publish Docker Images

To publish the Docker images to a registry, first build the worker base images as described in
[Local Setup](#local-setup). Then you can use the `jib` task to publish the images by setting the correct prefix for the
registry. You can also configure the tag which defaults to `latest`.

```shell
# Publish all Docker images.
./gradlew -PdockerImagePrefix=my.registry/ jib

# Publish one specific image.
./gradlew -PdockerImagePrefix=my.registry/ :core:jib

# Publish using a custom tag.
./gradlew -PdockerImagePrefix=my.registry/ -PdockerImageTag=custom jib
```

## License

See the [NOTICE](./NOTICE) file in the root of this project for the copyright details.

See the [LICENSE](./LICENSE) file in the root of this project for license details.
