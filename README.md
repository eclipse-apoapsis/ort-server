# <img alt="ORT Server" src="assets/logo.svg" width="10%"> Eclipse Apoapsis - ORT Server

The [Eclipse Apoapsis](https://projects.eclipse.org/projects/technology.apoapsis) project's **ORT Server** is a
standalone application to deploy the [OSS Review Toolkit](https://github.com/oss-review-toolkit/ort) as a service in the
cloud.

> [!NOTE]
> This project is currently in the [incubation phase](https://www.eclipse.org/projects/handbook/#incubation) at the
> Eclipse Foundation and working towards making the first release.
> Once released, the project will use semantic versioning, until then breaking changes can occur at any time.
> 
> <img alt="Eclipse Incubation" src="https://projects.eclipse.org/modules/custom/eclipsefdn/eclipsefdn_projects/images/project_state/incubating.png" width="10%">

## Community

To communicate with the developers, you can:
* Join the [Matrix chat](https://matrix.to/#/#apoapsis:matrix.eclipse.org).
* Start a GitHub [discussion](https://github.com/eclipse-apoapsis/ort-server/discussions).
* Join the [mailing list](https://accounts.eclipse.org/mailing-list/apoapsis-dev).

Please report any issues to the [issue tracker](https://github.com/eclipse-apoapsis/ort-server/issues).

Contributions are welcome, please see the [contributing guide](CONTRIBUTING.md) for more information.

## Running ORT Server

The easiest way to run the ORT Server for testing is to use [Docker Compose](https://docs.docker.com/compose/).
For a proper deployment to Kubernetes, the project will later provide a Helm chart.

### Docker Compose

> [!CAUTION]
> Do not use the Docker Compose setup in production as it uses multiple insecure defaults, like providing Keycloak
> without TLS.

To start the ORT Server with the required third-party services run:

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

##### Quickstart

Run:
1. `./gradlew buildAllImages`
2. `ORT_SERVER_IMAGE_PREFIX= ORT_SERVER_IMAGE_TAG=latest ORT_SERVER_CORE_PORT=8090 docker compose up -d`
3. Open http://localhost:8082/.
4. Log in as "admin" / "admin".

##### Detailed instructions

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

Then the Docker images containing the projects can be built via the Gradle task

```shell
# Build all images at once and any dependent base images:
./gradlew buildAllImages
```

To only build the base images for the workers, which contain the external tools and required configuration, plus the image for the UI either via the Gradle task

```shell
# Build all worker images at once:
./gradlew buildAllWorkerImages

# Build all worker images at once with custom build arguments:
./gradlew -PdockerBaseBuildArgs="TEMURIN_VERSION=11" buildAllWorkerImages
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

#### Handling major version upgrades of PostgreSQL

PostgreSQL does not support reading the data directory of a previous major version.
When upgrading the PostgreSQL version in the Docker Compose setup, the database data directory must be manually
migrated by following these steps:

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

### Local Kubernetes with Tilt

#### Setup

1. Install Tilt
2. Install OpenTofu
3. Install Minikube
4. Install ctlptl

Create the Minikube cluster with ctlptl:

```console
ctlptl create cluster minikube --registry=ctlptl-registry --minikube-start-flags "--memory=6g" --minikube-start-flags "--cpus=4"
```

Start ORT Server:

```console
tilt up
```

Destroy the cluster:

```console
ctlptl delete cluster minikube
```

To get the UI working, create file `/ui/.env.local` with the following content:

```env
VITE_CLIENT_ID=ort-server
VITE_AUTHORITY=http://localhost:8081/realms/ort-server
```

and run it with `pnpm -C ui dev`.


### Accessing the services

| Service        | URL                              | Credentials                                             |
|----------------|----------------------------------|---------------------------------------------------------|
| ORT Server API | http://localhost:8080/swagger-ui |                                                         |
| Keycloak       | http://localhost:8081            | Administrator: admin:admin<br/>User: ort-admin:password |
| UI             | http://localhost:8082            | Same as Keycloak                                        |
| PostgreSQL     | http://localhost:5433            | postgres:postgres                                       |
| RabbitMQ       | http://localhost:15672           | admin:admin                                             |
| Graphite       | http://localhost:8888            | root:root                                               |
| Grafana        | http://localhost:3200            | admin:1234                                              |

#### HTTP request collections

When using IntelliJ IDEA Ultimate, you can use the [integrated HTTP client](https://www.jetbrains.com/help/idea/http-client-in-product-code-editor.html) to execute requests against the ORT Server.
The requests can be found in [scripts/requests](./scripts/requests).

### Debugging

To debug the ORT Server in IntelliJ, you can use a composition with only some selected services:

```shell
docker compose up rabbitmq keycloak 
```

Please note that Postgres does not need to be explicitly passed: since it is a dependency of Keycloak, it will be
automatically started.

Then execute the ORT Server in IntelliJ with the run configuration "Run ORT Server".

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
