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
    id("ort-server-kotlin-jvm-conventions")
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
    api(projects.components.pluginManager.api)
    api(projects.model)

    implementation(projects.components.authorization.implementation)
    implementation(projects.dao)
    implementation(projects.shared.ktorUtils)

    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.core)
    implementation(libs.exposedCore)
    implementation(libs.exposedJson)
    implementation(libs.exposedKotlinDatetime)
    implementation(libs.kotlinResult)
    implementation(libs.ktorOpenApi)
    implementation(libs.ortAdvisor)
    implementation(libs.ortAnalyzer)
    implementation(libs.ortPackageConfigurationProviderApi)
    implementation(libs.ortPackageCurationProviderApi)
    implementation(libs.ortReporter)
    implementation(libs.ortScanner)

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

    testImplementation(platform(libs.ortAdvisors))
    testImplementation(platform(libs.ortPackageConfigurationProviders))
    testImplementation(platform(libs.ortPackageCurationProviders))
    testImplementation(platform(libs.ortPackageManagers))
    testImplementation(platform(libs.ortReporters))
    testImplementation(platform(libs.ortScanners))
}
