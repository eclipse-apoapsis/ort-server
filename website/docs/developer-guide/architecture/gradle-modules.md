# Gradle Modules

The project is currently in the middle of a transition from a legacy Gradle module structure to a new vertical slice structure.
The reason is that the legacy structure encouraged a high degree of coupling between code that should be independent, which made it hard to maintain and extend the code base.
It also made it hard to find the code for a specific feature, because it was by design spread across multiple modules.

This document provides a high-level overview of both architectures and the status of the transition.

## Legacy Structure

The legacy module structure follows a layered architecture, where the modules are grouped by their technical role in the application.

### `api`

The API modules contain the code for the REST API.
They are grouped by the API version to allow for different versions of the API to be implemented.
The modules for each API version are responsible for defining the API model, mapping between the [internal model](#model) and the API model, and implementing an API client.

### `cli`

The CLI module contains a command line interface for the ORT server, leveraging the API client.
This is useful for implementing scripts or other tools that interact with the ORT server, for example, to integrate it into a CI/CD pipeline.

### `clients`

The client modules contain independent clients, for example, for Keycloak, that can be used by the services.

### `config`

The config abstraction contains implementations to load configuration files and infrastructure secrets from different sources.

### `core`

The core module contains the Ktor backend including route definitions, dependency injection, and configuration.
It is also responsible for authentication.

### `dao`

The DAO module contains all code related to database access, for example, database-specific repository implementations.
A repository only has to be implemented for an [aggregate root](https://martinfowler.com/bliki/DDD_Aggregate.html) and not for every table.
An aggregate root is the most important entity object, which encapsulates entity/value objects.
Because of this, only the business model classes representing aggregate roots have a property for the database id.
For all other classes, this is considered an implementation detail of the DAO layer.
Note that we are currently not strict with regard to DDD and allow multiple repositories to access the same tables, for example, both analyzer and scanner runs contain an environment object and store its information in the same tables.

### `logaccess`

The logaccess abstraction contains implementations to access log files from different sources, for example, log aggregation services.

### `model`

The model module contains the business model of the ORT server.
This includes classes that represent server specific concepts, as well as classes that represent the ORT model.
For the most part, the ORT Server does not directly use the ORT model classes, but instead maps them to its own model classes to allow for a more flexible handling of breaking changes in the ORT model.

### `orchestrator`

The orchestrator module contains the code for the Orchestrator, which is responsible for scheduling the different jobs of an ORT run.

### `secrets`

The secrets abstraction contains implementations to access secrets from different secret management services.

### `services`

The service modules contain business logic that is used by the different applications.

### `storage`

The storage abstraction contains implementations to store binary data in different storage systems.

### `tasks`

The tasks module contains the logic for regular background tasks that are executed by the ORT Server, for example, to clean up old data or to monitor Kubernetes jobs.

### `transport`

The transport abstraction contains implementations for different messaging systems.

### `utils`

The utils modules contain utility functions that are used across the project.

### `workers`

The worker modules contain code that runs the individual ORT tools, like the analyzer.
The workers are individual applications that run independently of the `core` and `orchestrator` applications.

## New Vertical Slice Structure

The new vertical slice structure groups the modules by feature, instead of by technical role.
This allows for a better separation of concerns and makes it easier to find the code for a specific feature, because most of it is located in one module.

It also reduces the risk of introducing coupling between features that should be independent, because the modules are more self-contained and dependencies on other features are more explicit.
For example, with the legacy architecture, any module that depends on the `dao` module can access all database tables.
This makes it easy to violate the boundaries between features, for example, by accessing tables that are not part of the feature and should only be accessed via their aggregate root.

### Overview

This graph shows the target structure of the ORT server modules after the transition is complete.
The order of the top-level items in the graph shows the direction of dependencies between modules:
Modules can depend on other modules above them, but not on modules below them.
For example, a `component` can depend on `shared` and `infrastructure` modules, but not on `application` modules.

```
ort-server
├── shared (stateless utility code, models)
│   ├── keycloak-client
│   ├── ktor-utils
│   └── ...
├── infrastructure (connections to external systems)
│   ├── transport
│   │   ├── rabbitmq
│   │   └── ...
│   ├── secrets
│   │   ├── vault
│   │   └── ...
├── components (stateful vertical slices)
│   ├── plugin-manager
│   │   ├── api-model
│   │   └── backend
│   ├── hierarchy
│   │   ├── api-model
│   │   └── backend
│   └── ...
├── compositions (functionality using multiple components)
│   ├── secrets-routes
│   └── ...
└── applications (deployable applications)
    ├── core
    ├── orchestrator
    ├── analyzer-worker
    └── ...
```

### `shared`

The `shared` modules contain stateless utility code and models that are used across the project.

### `infrastructure`

The `infrastructure` modules contain code that connects to external systems, for example, messaging systems or secret management services.

### `components`

The `components` modules contain stateful vertical slices that implement a specific feature of the ORT server.
A vertical slice is a self-contained module that implements a feature from the API to the database.

The `components` modules are split into two sub-modules:

- `api-model`: This is a Kotlin multiplatform module that contains the API model of the feature.
- `backend`: This module contains the implementation of the feature, including the business logic.
  Backend modules should keep most of their code `internal` to enforce encapsulation and maintain a clear boundary between modules.
  They usually expose functionality via a service class.

It is not forbidden that a `component` depends on another `component`, but this should be avoided if possible, because it increases coupling between features.

### `compositions`

The `compositions` modules contain functionality that uses multiple `components` to implement a feature.
This is needed when implementing a feature would require a circular dependency between `components`, which is technically not possible.

For example, the `secret-routes` module implements the feature of deleting secrets which must check if a `Secret` is still referenced by an infrastructure service.
Implementing this within the `secrets` component is not possible, because the `infrastructure-service` component already depends on the `secrets` component, which would create a circular dependency.

### `applications`

The `applications` modules contain the deployable applications of the ORT server, for example, the `core` and `orchestrator` applications.
For deployment, each of these applications is packaged into a Docker image.

## Transition Status

| Section          | Status      | Description                                                                                                                                           |
| ---------------- | ----------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| `shared`         | In Progress | The `shared` modules are being created on demand when migrating features to `component`s.                                                             |
| `infrastructure` | Not Started | All infrastructure modules are still on the top-level.                                                                                                |
| `components`     | In Progress | New features are mostly implemented as components, but a lot of existing functionality still needs to be migrated.                                    |
| `compositions`   | In Progress | The `composition` modules are being created on demand when extracting a `component` where part of the functionality would violate feature boundaries. |
| `applications`   | Not Started | All applications are in their legacy location.                                                                                                        |
