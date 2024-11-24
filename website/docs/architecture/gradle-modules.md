# Gradle Modules

A high-level overview of the Gradle modules can be found below.

## API

The API modules contain the code for the REST API.
They are grouped by the API version to allow for different versions of the API to be implemented.
The modules for each API version are responsible for defining the API model, mapping between the [internal model](#model) and the API model, and implementing an API client.

## CLI

The CLI module contains a command line interface for the ORT server, levering the API client.
This is useful for implementing scripts or other tools that interact with the ORT server, for example, to integrate it into a CI/CD pipeline.

## Clients

The client modules contain independent clients, for example, for Keycloak, that can be used by the services.

## Config

The config abstraction contains implementations to load configuration files and infrastructure secrets from different sources.

## Core

The core module contains the Ktor backend including route definitions, dependency injection, and configuration.
It is also responsible for authentication.

## DAO

The DAO module contains all code related to database access, for example, database-specific repository implementations.
A repository only has to be implemented for an [aggregate root](https://martinfowler.com/bliki/DDD_Aggregate.html) and not for every table.
An aggregate root is the most important entity object, which encapsulates entity/value objects.
Because of this, only the business model classes representing aggregate roots have a property for the database id.
For all other classes, this is considered an implementation detail of the DAO layer.
Note that we are currently not strict with regard to DDD and allow multiple repositories to access the same tables, for example, both analyzer and scanner runs contain an environment object and store its information in the same tables.

## Kubernetes

The Kubernetes module contains utilities related to deploying the ORT server to Kubernetes.

## Logaccess

The logaccess abstraction contains implementations to access log files from different sources, for example, log aggregation services.

## Model

The model module contains the internally used class and interface definitions.

## Orchestrator

The orchestrator module contains the code for the Orchestrator, which is responsible for scheduling the different jobs of an ORT run.

## Secrets

The secrets abstraction contains implementations to access secrets from different secret management services.

## Services

The services modules contain different aspects of the business logic.

## Storage

The storage abstraction contains implementations to store binary data in different storage systems.

## Transport

The transport abstraction contains implementations for different messaging systems.

## Utils

The utils modules contain utility functions that are used across the project.

## Workers

The worker modules contain code that run the individual ORT tools, like the analyzer.
They run independently of the backend to process the jobs of an ORT run.
