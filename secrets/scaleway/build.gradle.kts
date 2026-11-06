/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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
    // Apply precompiled plugins.
    id("ort-server-kotlin-jvm-conventions")
    id("ort-server-publication-conventions")

    // Apply third-party plugins.
    alias(libs.plugins.kotlinSerialization)
}

group = "org.eclipse.apoapsis.ortserver.secrets"

dependencies {
    api(projects.secrets.secretsSpi)

    implementation(projects.shared.ktorClientUtils)
    implementation(projects.utils.config)
    implementation(projects.utils.logging)

    implementation(ktorLibs.client.contentNegotiation)
    implementation(ktorLibs.client.core)
    implementation(ktorLibs.serialization.kotlinx.json)

    testImplementation(testFixtures(projects.config.configSpi))

    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.wiremock)
}

tasks.named<Test>("test") {
    // Some tests run against the real Scaleway API if credentials are provided via environment variables. As their
    // results depend on the state of the remote API, always run the tests and do not cache them in this case.
    val hasCredentials = providers.environmentVariable("SCW_SECRET_KEY")
        .zip(providers.environmentVariable("SCW_PROJECT_ID")) { secretKey, projectId ->
            secretKey.isNotEmpty() && projectId.isNotEmpty()
        }.orElse(false)

    outputs.upToDateWhen { !hasCredentials.get() }
    outputs.cacheIf("Scaleway credentials are not provided") { !hasCredentials.get() }
}
