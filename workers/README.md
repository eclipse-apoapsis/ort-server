# Worker Docker images

The module *workers* contains submodules for each tool of ORT: Analyzer, Advisor, Scanner, Evaluator and Reporter.

For each tool, the submodule contains the code of the workers running in the cluster and the configuration to build the
Docker images for them. These Docker images are constructed by [Jib](https://github.com/GoogleContainerTools/jib) from
a *base image*. The latter contains all the tools required by the worker, but not the Java/Kotlin code of the worker
which will be added by Jib.

Since this base image changes less often than the actual worker code, this has the benefit of optimizing the build by
quickly rebuilding the worker's Docker image while reusing the already built base image.

The project structure is the following:

```
workers/
├── analyzer                          // Submodule for the Analyzer tool
│   ├── build.gradle.kts              // Gradle build file with the Jib configuration
│   ├── docker
│   │   ├── Analyzer.Dockerfile       // Docker file for the base image
│   │   └── scripts                   // Scripts required by the base docker image
│   └── src
│       └── main
│           └── kotlin
│               └── analyzer          // Source code of the Analyzer worker
│                   └── Entrypoint.kt // Entrypoint of the Docker image
└── Readme.md // This file
```

Choosing the worker tool for which to build the Docker image is made by
selecting the correct submodule in the `workers` module.

The following example presents how to build a Docker image for the **Analyzer** worker tool:

* for a local Docker daemon
* for an Azure Container Registry (ACR)

The Docker images produced by these steps will be:

* *ort-server-worker-base-image:analyzer-latest*: the base image for the Analyzer worker
* *ort-server-analyzer-worker*: the image for the Analyzer worker

## For a local Docker Daemon

Build the base image for the Analyzer. This is the same image as upstream ORT but without ORT and Scancode:

```docker build . -f Analyzer.Dockerfile -t ort-server-analyzer-worker-base-image:latest```

Build the Analyzer worker Docker image:

```./gradlew :workers:analyzer:tinyJibDocker```

Run the image:

```docker run ort-server-analyzer-worker```

## For an Azure container registry

Set the name of your ACR in an environment variable, without the *.azurecr.io* extension:

```export ACR_NAME=<name of your ACR>```

Login to Azure ACR:

```
az login
az acr login --name $ACR_NAME
```

### Build and publish the base image

Build the base image for Analyzer. This step is identical as above.

```docker build . -f Analyzer.Dockerfile -t ort-server-worker-base-image:analyzer-latest```

Tag the base image with the fully qualified path to the registry:

```docker tag ort-server-worker-base-image:analyzer-latest $ACR_NAME.azurecr.io/ort-server-worker:analyzer-baseimage-latest```

Push the base image to the ACR:

```docker push $ACR_NAME.azurecr.io/ort-server-worker:analyzer-baseimage-latest```

### Build the Analyzer Docker image

Build the Analyzer worker Docker image:

```./gradlew :workers:analyzer:tinyJibDocker -Djib.from.image=$ACR_NAME.azurecr.io/ort-server-worker:analyzer-baseimage-latest -Djib.to.image=$ACR_NAME.azurecr.io/ort-server-worker:analyzer-latest -PworkerTool=analyzer```

Push the Analyzer worker Docker image to the ACR:

```docker push $ACR_NAME.azurecr.io/ort-server-worker:analyzer-latest```
