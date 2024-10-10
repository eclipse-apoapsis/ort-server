/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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
}

group = "org.eclipse.apoapsis.ortserver.config"

dependencies {
    api(libs.typesafeConfig)

    implementation(projects.config.configSpi)
    implementation(projects.utils.config)
    implementation(projects.utils.logging)

    implementation(libs.kotlinxDatetime)
    implementation(libs.ktorClientOkHttp)
    implementation(libs.ktorIo)
    implementation(libs.ktorKotlinxSerialization)

    testImplementation(testFixtures(projects.config.configSpi))
    testImplementation(projects.utils.test)

    testImplementation(libs.kotestAssertionsCore)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.mockk)
    testImplementation(libs.wiremockStandalone)
}
