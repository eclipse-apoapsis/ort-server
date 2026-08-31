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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Apply core plugins.
    `java-test-fixtures`

    // Apply precompiled plugins.
    id("ort-server-koin-conventions")
    id("ort-server-kotlin-jvm-conventions")
    id("ort-server-publication-conventions")

    // Apply third-party plugins.
    alias(libs.plugins.kotlinSerialization)
}

group = "org.eclipse.apoapsis.ortserver"

dependencies {
    api(projects.config.configSpi)
    api(projects.model)

    implementation(projects.utils.config)
    implementation(projects.utils.system)

    api(libs.exposed.core)
    api(libs.exposed.dao)
    api(libs.exposed.jdbc)
    api(libs.koin.core)
    api(libs.kotlinx.serialization.json)

    implementation(libs.bundles.flyway)
    implementation(libs.exposed.json)
    implementation(libs.exposed.kotlinDatetime)
    implementation(libs.hikari)
    implementation(libs.postgres)
    implementation(libs.typesafeConfig)

    runtimeOnly(libs.exposed.jdbc)
    runtimeOnly(libs.logback)

    testImplementation(testFixtures(projects.config.configSpi))

    testImplementation(libs.koin.test)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.extensions.koin)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.mockk)
    testImplementation(libs.testContainers.postgresql)

    testFixturesApi(projects.model)

    testFixturesImplementation(projects.config.configSpi)
    testFixturesImplementation(projects.utils.test)

    testFixturesImplementation(libs.flyway.core)
    testFixturesImplementation(libs.hikari)
    testFixturesImplementation(libs.koin.test)
    testFixturesImplementation(libs.kotest.extensions.testcontainers)
    testFixturesImplementation(libs.kotest.runner.junit5)
    testFixturesImplementation(libs.mockk)
    testFixturesImplementation(libs.testContainers)
    testFixturesImplementation(libs.testContainers.postgresql)
}
