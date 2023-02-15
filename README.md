# ORT Server

The ORT server is a standalone application to deploy the
[OSS Review Toolkit](https://github.com/oss-review-toolkit/ort) as a service in the cloud.

## Local Setup

To start the ORT server with the required 3rd party services, you can use
[Docker Compose](https://docs.docker.com/compose/).

First, build the analyzer worker base image which contains the external tools used by the analyzer:

```shell
cd workers/analyzer/docker
DOCKER_BUILDKIT=1 docker build . -f Analyzer.Dockerfile -t ort-server-analyzer-worker-base-image:latest
```

Then build all Docker images with [Jib](https://github.com/GoogleContainerTools/jib):

```shell
./gradlew jibDockerBuild
```

Finally, you can start Docker Compose. Since the choice between ActiveMQ Artemis and RabbitMQ is offered, you need to choose the
one to activate with a [profile](https://docs.docker.com/compose/profiles/):

```shell
docker compose --profile rabbitmq up
```

**Do not use the Docker Compose setup in production as it uses multiple insecure defaults, like providing KeyCloak
without TLS.**

## Accessing the services

| Service        | URL                                       |
|----------------|-------------------------------------------|
| ORT Server API | http://localhost:8080/swagger-ui          | 
| Keycloak       | http://localhost:8081 (admin:admin)       |
| PostgreSQL     | http://localhost:5433 (postgres:postgres) |
| RabbitMQ       | http://127.0.0.1:15672/#/                 |

## Debugging

To debug the ORT server in IntelliJ, you can use a composition with only some selected services:

```shell
docker compose --profile rabbitmq up rabbitmq keycloak 
```

Please note that Postgres does not need to be explicitly passed: since it is a dependency of Keycloak, it will be
automatically started.

Then execute the ORT server in IntelliJ with the run configuration "Run ORT Server".

# License

See the [NOTICE](./NOTICE) file in the root of this project for the copyright details.

See the [LICENSE](./LICENSE) file in the root of this project for license details.

OSS Review Toolkit (ORT) is a [Linux Foundation project](https://www.linuxfoundation.org) and part of
[ACT](https://automatecompliance.org/).

# Troubleshooting

When starting the ORT Server service you can run into the following error:
`Exception in thread "main" org.flywaydb.core.api.exception.FlywayValidateException: Validate failed: Migrations have failed validation`

You can solve this problem by cleaning the database. Please note that it will empty all ORT Server table.

```shell
docker compose -f docker-compose.yml -f docker-compose-maintenance.yml up flyway
```

