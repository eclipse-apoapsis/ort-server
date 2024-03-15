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

plugins {
    application
    `java-test-fixtures`

    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinxSerialization)
}

group = "org.eclipse.apoapsis.ortserver.workers"

repositories {
    mavenCentral()
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    implementation(projects.dao)
    implementation(projects.model)
    implementation(projects.secrets.secretsSpi)

    implementation(libs.kaml)
    implementation(libs.kotlinxCoroutines)

    api(projects.config.configSpi)
    api(projects.storage.storageSpi)

    api(libs.jacksonModuleKotlin)
    api(libs.koinCore)
    api(libs.ortModel)
    api(libs.ortScanner)
    api(libs.typesafeConfig)

    testImplementation(testFixtures(projects.config.configSpi))
    testImplementation(testFixtures(projects.dao))
    testImplementation(testFixtures(projects.secrets.secretsSpi))
    testImplementation(testFixtures(projects.storage.storageSpi))
    testImplementation(projects.utils.test)

    testImplementation(libs.kotestAssertionsCore)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.mockk)

    testFixturesApi(libs.kotlinxDatetime)
    testFixturesApi(libs.ortModel)
}
