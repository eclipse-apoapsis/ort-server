/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

group = "org.ossreviewtoolkit.server.dao"
version = "0.0.1"

@Suppress("DSL_SCOPE_VIOLATION") // See https://youtrack.jetbrains.com/issue/KTIJ-19369.
plugins {
    alias(libs.plugins.kotlinJvm)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    implementation(libs.exposedCore)
    implementation(libs.exposedDao)
    implementation(libs.exposedJavaTime)
    implementation(libs.exposedJdbc)
    implementation(libs.flywayCore)
    implementation(libs.hikari)
    implementation(libs.postgres)
    implementation(libs.logback)

    testImplementation(libs.kotestAssertionsCore)
    testImplementation(libs.kotestAssertionsKtor)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestExtensionsTestContainer)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.testContainers)
    testImplementation(libs.testContainersPostgresql)
}
