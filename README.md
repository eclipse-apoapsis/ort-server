# ORT Server

The ORT server is a standalone application to deploy the
[OSS Review Toolkit](https://github.com/oss-review-toolkit/ort) as a service in the cloud.

## Local Setup

To start the ORT server with the required 3rd party services, you can use
[Docker Compose](https://docs.docker.com/compose/).

First, ensure to have [Docker BuildKit](https://docs.docker.com/build/buildkit/) enabled by either using Docker version
23.0 or newer, running `export DOCKER_BUILDKIT=1`, or configuring `/etc/docker/daemon.json` with

```json
{
  "features": {
    "buildkit": true
  }
}
```

Then build the base images for the workers which contain the external tools and required configuration:

```shell
docker build workers/analyzer/docker -f workers/analyzer/docker/Analyzer.Dockerfile -t ort-server-analyzer-worker-base-image
docker build workers/config/docker -f workers/config/docker/Config.Dockerfile -t ort-server-config-worker-base-image
docker build workers/evaluator/docker -f workers/evaluator/docker/Evaluator.Dockerfile -t ort-server-evaluator-worker-base-image
docker build workers/notifier/docker -f workers/notifier/docker/Notifier.Dockerfile -t ort-server-notifier-worker-base-image
docker build workers/reporter/docker -f workers/reporter/docker/Reporter.Dockerfile -t ort-server-reporter-worker-base-image
docker build workers/scanner/docker -f workers/scanner/docker/Scanner.Dockerfile -t ort-server-scanner-worker-base-image
```

For analyzing Java projects, it must be ensured that the Java version used by the Analyzer worker is compatible with
the JDK used by the project. If the project requires a newer Java version, you might see `UnsupportedClassVersionError`
exceptions; projects running on an old Java version can cause problems as well. To deal with such problems, it is
possible to customize the Java version in the container image for the Analyzer worker. This is done via the
`TEMURIN_VERSION` build argument. Per default, the version is set to a JDK 17. To change this, pass a build argument
with the desired target version, for instance for targeting Java 11:

```shell
docker build --build-arg="TEMURIN_VERSION=11" . -f Analyzer.Dockerfile -t ort-server-analyzer-worker-base-image:11-latest
```

Then the Docker images can be built with [Jib](https://github.com/GoogleContainerTools/jib):

```shell
# Build all images at once:
./gradlew jibDockerBuild

# Build one specific image, for example for the `core` module:
./gradlew :core:jibDockerBuild
```

The `jibDockerBuild` task occasionally gets stuck while pulling the Eclipse Temurin base image. In this case it usually
helps to clean the project and stop the Gradle Daemon:

```shell
./gradlew clean
./gradlew --stop
```

When building multiple images at once it can also help to disable parallel builds:

```shell
./gradlew --no-parallel jibDockerBuild
```

In case multiple base images have been created for the Analyzer supporting different Java versions, the tag of the base
image to be used can be specified as a property. Note that the JDK on which the build is executed determines the JVM
target of the resulting artifacts. So, if a specialized Analyzer image for Java 11 is to be created, the build must be
done on a JDK 11 as well:

```shell
$ java -version
openjdk version "11.0.22" 2024-01-16
OpenJDK Runtime Environment Temurin-11.0.22+7 (build 11.0.22+7)
OpenJDK 64-Bit Server VM Temurin-11.0.22+7 (build 11.0.22+7, mixed mode)

$ ./gradlew -PdockerBaseImageTag=11-latest \
    -PdockerImageTag=11-latest \
    :workers:analyzer:jibDockerBuild
```

Here, the `dockerBaseImageTag` parameter specifies the tag of the Analyzer base image to be used. The
`dockerImageTag` parameter controls the tag assigned to the newly created Analyzer image. It is _latest_ by default.
This example sets the same tag for both the base image and the final Analyzer image, which is certainly a reasonable
convention.

Finally, you can start Docker Compose:

```shell
docker compose up
```

**Do not use the Docker Compose setup in production as it uses multiple insecure defaults, like providing KeyCloak
without TLS.**

### Accessing the services

| Service        | URL                              | Credentials                                             |
|----------------|----------------------------------|---------------------------------------------------------|
| ORT Server API | http://localhost:8080/swagger-ui |                                                         |
| Keycloak       | http://localhost:8081            | Administrator: admin:admin<br/>User: ort-admin:password |
| PostgreSQL     | http://localhost:5433            | postgres:postgres                                       |
| RabbitMQ       | http://localhost:15672           | admin:admin                                             |
| Graphite       | http://localhost:8888            | root:root                                               |

#### HTTP request collections

When using IntelliJ IDEA Ultimate, you can use the [integrated HTTP client](https://www.jetbrains.com/help/idea/http-client-in-product-code-editor.html) to execute requests against the ORT server.
The requests can be found in [scripts/requests](./scripts/requests).

### Debugging

To debug the ORT server in IntelliJ, you can use a composition with only some selected services:

```shell
docker compose up rabbitmq keycloak 
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
registry. You can also configure the tag which defaults to `latest`. When publishing multiple images at once it is
recommended to disable parallel builds.

```shell
# Publish all Docker images.
./gradlew --no-parallel -PdockerImagePrefix=my.registry/ jib

# Publish one specific image.
./gradlew -PdockerImagePrefix=my.registry/ :core:jib

# Publish using a custom tag.
./gradlew --no-parallel -PdockerImagePrefix=my.registry/ -PdockerImageTag=custom jib
```

## License

See the [NOTICE](./NOTICE) file in the root of this project for the copyright details.

See the [LICENSE](./LICENSE) file in the root of this project for license details.
