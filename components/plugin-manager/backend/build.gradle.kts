/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

plugins {
    id("ort-server-kotlin-component-backend-conventions")
    id("ort-server-publication-conventions")

    // Apply third-party plugins.
    alias(libs.plugins.kotlinSerialization)
}

group = "org.eclipse.apoapsis.ortserver.components.pluginmanager"

repositories {
    exclusiveContent {
        forRepository {
            maven("https://repo.gradle.org/gradle/libs-releases/")
        }

        filter {
            includeGroup("org.gradle")
        }
    }
}

dependencies {
    api(projects.components.pluginManager.apiModel)
    api(projects.model)

    implementation(projects.dao)

    implementation(libs.exposedCore)
    implementation(libs.exposedJson)
    implementation(libs.exposedKotlinDatetime)
    implementation(libs.kotlinResult)
    implementation(ortLibs.advisor)
    implementation(ortLibs.analyzer)
    implementation(ortLibs.ortPlugins.packageConfigurationProviders.api)
    implementation(ortLibs.ortPlugins.packageCurationProviders.api)
    implementation(ortLibs.reporter)
    implementation(ortLibs.scanner)

    routesImplementation(projects.components.authorization.backend)
    routesImplementation(projects.shared.ktorUtils)

    routesImplementation(ktorLibs.server.auth)
    routesImplementation(ktorLibs.server.core)
    routesImplementation(libs.ktorOpenApi)

    testImplementation(testFixtures(projects.clients.keycloak))
    testImplementation(testFixtures(projects.dao))
    testImplementation(testFixtures(projects.shared.ktorUtils))

    testImplementation(ktorLibs.serialization.kotlinx.json)
    testImplementation(ktorLibs.server.auth)
    testImplementation(ktorLibs.server.contentNegotiation)
    testImplementation(ktorLibs.server.statusPages)
    testImplementation(ktorLibs.server.testHost)
    testImplementation(libs.kotestAssertionsCore)
    testImplementation(libs.kotestAssertionsKtor)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotlinxSerializationJson)
    testImplementation(libs.mockk)

    testImplementation(platform(ortLibs.ortPlugins.advisors))
    testImplementation(platform(ortLibs.ortPlugins.packageConfigurationProviders))
    testImplementation(platform(ortLibs.ortPlugins.packageCurationProviders))
    testImplementation(platform(ortLibs.ortPlugins.packageManagers))
    testImplementation(platform(ortLibs.ortPlugins.reporters))
    testImplementation(platform(ortLibs.ortPlugins.scanners))
}
