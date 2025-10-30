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
include(":components:admin-config:api-model")
include(":components:admin-config:backend")
include(":components:authorization:api-model")
include(":components:authorization:backend")
include(":components:authorization-keycloak:backend")
include(":components:infrastructure-services:api-model")
include(":components:infrastructure-services:backend")
include(":components:plugin-manager:api-model")
include(":components:plugin-manager:backend")
include(":components:resolutions:api-model")
include(":components:resolutions:backend")
include(":components:search:api-model")
include(":components:search:backend")
include(":components:secrets:api-model")
include(":components:secrets:backend")
include(":compositions:secrets-routes")
include(":config")
include(":config:git")
include(":config:github")
include(":config:local")
include(":config:secret-file")
include(":config:spi")
include(":core")
include(":dao")
include(":logaccess")
include(":logaccess:spi")
include(":logaccess:loki")
include(":model")
include(":orchestrator")
include(":secrets")
include(":secrets:azure-keyvault")
include(":secrets:file")
include(":secrets:spi")
include(":secrets:scaleway")
include(":secrets:vault")
include(":services:admin-config")
include(":services:content-management")
include(":services:hierarchy")
include(":services:ort-run")
include(":services:report-storage")
include(":shared:api-mappings")
include(":shared:api-model")
include(":shared:ktor-utils")
include(":shared:ort-test-data")
include(":shared:package-curation-providers")
include(":shared:plugin-info")
include(":shared:reporters")
include(":storage")
include(":storage:azure-blob")
include(":storage:database")
include(":storage:s3")
include(":storage:spi")
include(":tasks")
include(":transport")
include(":transport:activemqartemis")
include(":transport:azure-servicebus")
include(":transport:kubernetes")
include(":transport:rabbitmq")
include(":transport:spi")
include(":transport:sqs")
include(":utils:config")
include(":utils:logging")
include(":utils:system")
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

// Append "-service" to all service project names.
rootProject.children.single { it.name == "services" }.children.forEach { it.name = "${it.name}-service" }

// Append "-worker" to all worker project names.
rootProject.children.single { it.name == "workers" }.children
    .filter { it.name != "common" }
    .forEach { it.name = "${it.name}-worker" }

// Prefix all SPI project names with their parent project name.
rootProject.children.forEach { child ->
    child.children.singleOrNull { it.name == "spi" }?.name = "${child.name}-spi"
}

// Prefix all component subprojects names with their parent project name.
rootProject.children.single { it.name == "components" }.children.forEach { component ->
    component.children.forEach { it.name = "${component.name}-${it.name}" }
}

plugins {
    // Gradle cannot access the version catalog from here, so hard-code the dependency.
    id("org.gradle.toolchains.foojay-resolver-convention").version("1.0.0")
}

dependencyResolutionManagement {
    repositories {
        mavenLocal {
            name = "localOrt"

            content {
                includeGroupAndSubgroups("org.ossreviewtoolkit")
            }
        }

        mavenCentral()
    }

    versionCatalogs {
        create("ktorLibs") {
            from("io.ktor:ktor-version-catalog:3.3.3")
        }

        create("ortLibs") {
            from("org.ossreviewtoolkit:version-catalog:76.0.0")
        }
    }
}
