/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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
    `java-test-fixtures`

    alias(libs.plugins.kotlinJvm)
}

group = "org.ossreviewtoolkit.server.logaccess"

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    api(projects.config.configSpi)

    api(libs.kotlinxDatetime)

    implementation(projects.utils.config)

    implementation(libs.kotlinxCoroutines)
    implementation(libs.ortCommonUtils)
    implementation(libs.slf4j)

    testImplementation(libs.kotestAssertionsCore)
    testImplementation(libs.kotestExtensionsTestContainer)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.mockk)

    testFixturesImplementation(projects.utils.config)
}
