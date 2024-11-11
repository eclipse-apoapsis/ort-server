/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

// Enable type-safe project accessors, see:
// https://docs.gradle.org/current/userguide/declaring_dependencies.html#sec:type-safe-project-accessors
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "ort-server"

include(":api:v1:client")
include(":api:v1:mapping")
include(":api:v1:model")
include(":cli")
include(":clients:keycloak")
include(":config:git")
include(":config:github")
include(":config:local")
include(":config:secret-file")
include(":config:spi")
include(":core")
include(":dao")
include(":logaccess:spi")
include(":logaccess:loki")
include(":model")
include(":orchestrator")
include(":secrets:file")
include(":secrets:spi")
include(":secrets:scaleway")
include(":secrets:vault")
include(":services:authorization")
include(":services:hierarchy")
include(":services:infrastructure")
include(":services:report-storage")
include(":services:secret")
include(":storage:database")
include(":storage:s3")
include(":storage:spi")
include(":transport:activemqartemis")
include(":transport:kubernetes")
include(":transport:kubernetes-jobmonitor")
include(":transport:rabbitmq")
include(":transport:spi")
include(":transport:sqs")
include(":utils:config")
include(":utils:logging")
include(":utils:test")
include(":workers:advisor")
include(":workers:analyzer")
include(":workers:common")
include(":workers:config")
include(":workers:evaluator")
include(":workers:notifier")
include(":workers:reporter")
include(":workers:scanner")

project(":api:v1:client").name = "api-v1-client"
project(":api:v1:mapping").name = "api-v1-mapping"
project(":api:v1:model").name = "api-v1-model"
project(":config:spi").name = "config-spi"
project(":logaccess:spi").name = "logaccess-spi"
project(":secrets:spi").name = "secrets-spi"
project(":services:authorization").name = "authorization-service"
project(":services:hierarchy").name = "hierarchy-service"
project(":services:infrastructure").name = "infrastructure-service"
project(":services:report-storage").name = "report-storage-service"
project(":services:secret").name = "secret-service"
project(":storage:spi").name = "storage-spi"
project(":transport:spi").name = "transport-spi"
project(":workers:config").name = "config-worker"

plugins {
    // Gradle cannot access the version catalog from here, so hard-code the dependency.
    id("org.gradle.toolchains.foojay-resolver-convention").version("0.8.0")
}
