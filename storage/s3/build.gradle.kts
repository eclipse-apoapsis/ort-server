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
    // Apply core plugins.
    `java-test-fixtures`

    // Apply precompiled plugins.
    id("ort-server-kotlin-jvm-conventions")
    id("ort-server-publication-conventions")
}

group = "org.eclipse.apoapsis.ortserver.storage"

dependencies {
    api(projects.storage.storageSpi)

    implementation(projects.utils.config)
    implementation(projects.utils.logging)

    implementation(libs.ortUtils)
    implementation(libs.s3)

    testImplementation(libs.kotestAssertionsCore)
    testImplementation(libs.kotestExtensionsTestContainer)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.testContainersLocalStack)

    testFixturesRuntimeOnly(libs.testContainers)
}
