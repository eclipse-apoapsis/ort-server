# ORT Server

The ORT server is a standalone application to deploy the
[OSS Review Toolkit](https://github.com/oss-review-toolkit/ort) as a service in the cloud.

> [!NOTE]
> This project is currently in the [incubation phase](https://projects.eclipse.org/projects/technology.apoapsis) at the
> Eclipse Foundation and working towards making the first release.
> Once released, the project will use semantic versioning, until then breaking changes can occur at any time. 

## Running ORT Server

The easiest way to run the ORT Server for testing is to use [Docker Compose](https://docs.docker.com/compose/).
For a proper deployment to Kubernetes, the project will later provide a Helm chart.

### Docker Compose

> [!CAUTION]
> Do not use the Docker Compose setup in production as it uses multiple insecure defaults, like providing Keycloak
> without TLS.

To start the ORT server with the required 3rd party services, you can run:

```shell
docker compose up
```

This will use the ORT Server images
[published on GitHub](https://github.com/orgs/eclipse-apoapsis/packages?ecosystem=container).
By default, the `main` tag is used.
Those images are built from the `main` branch of the repository.
To use a different tag, you can set the `ORT_SERVER_IMAGE_TAG` environment variable, for example:

```shell
ORT_SERVER_IMAGE_TAG=0.1.0-SNAPSHOT-001.sha.aa4d3fa docker compose up
```

By default, the ORT Server API is exposed on port 8080.
If this port is already in use, it can be changed using the `ORT_SERVER_CORE_PORT` environment variable:

```shell
ORT_SERVER_CORE_PORT=8090 docker compose up
```

When using a different port, please make sure that it is not used by [another service](#accessing-the-services).

#### Running with local images

During development, it is useful to run the ORT Server with locally built Docker images.

First, ensure to have [Docker BuildKit](https://docs.docker.com/build/buildkit/) enabled by either using Docker version
23.0 or newer, running `export DOCKER_BUILDKIT=1`, or configuring `/etc/docker/daemon.json` with

```json
{
  "features": {
    "buildkit": true
  }
}
```

To build the Docker image for the UI, the OpenAPI specification must be generated first:

```shell
./gradlew :core:generateOpenApiSpec
```

Then build the base images for the workers which contain the external tools and required configuration either via the Gradle task

```shell
# Build all worker images at once:
./gradlew buildAllWorkerImages

# Build all worker images at once with custom build arguments:
./gradlew -PdockerBaseBuildArgs="TEMURIN_VERSION=11" buildAllWorkerImages
```

*or* manually by running

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

Then the Docker images containing the projects can be built via the Gradle task

```shell
# Build all images at once and any dependent base images:
./gradlew buildAllImages
```

*or* manually via [Jib](https://github.com/GoogleContainerTools/jib) tasks by running

```shell
# Build all images at once:
./gradlew jibDockerBuild

# Build one specific image, for example for the `core` module:
./gradlew :core:jibDockerBuild
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

Finally, you can start Docker Compose using the local images by setting the image prefix to an empty string and the tag
to `latest`:

```shell
ORT_SERVER_IMAGE_PREFIX= ORT_SERVER_IMAGE_TAG=latest docker compose up
```

### Accessing the services

| Service        | URL                              | Credentials                                             |
|----------------|----------------------------------|---------------------------------------------------------|
| ORT Server API | http://localhost:8080/swagger-ui |                                                         |
| Keycloak       | http://localhost:8081            | Administrator: admin:admin<br/>User: ort-admin:password |
| UI             | http://localhost:8082            | Same as Keycloak                                        |
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
[Running with local images](#running-with-local-images).
Then you can use the `jib` task to publish the images by setting the correct prefix for the registry.
You can also configure the tag which defaults to `latest`.

```shell
# Publish all Docker images.
./gradlew -PdockerImagePrefix=my.registry/ jib

# Publish one specific image.
./gradlew -PdockerImagePrefix=my.registry/ :core:jib

# Publish using a custom tag.
./gradlew -PdockerImagePrefix=my.registry/ -PdockerImageTag=custom jib
```

## Generate OpenAPI specification

The OpenAPI specification can be generated by running this Gradle task:

```shell
./gradlew :core:generateOpenApiSpec
```

The task writes the specification to `ui/build/openapi.json`.

## License

See the [NOTICE](./NOTICE) file in the root of this project for the copyright details.

See the [LICENSE](./LICENSE) file in the root of this project for license details.
