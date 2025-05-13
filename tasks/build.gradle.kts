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

import com.google.cloud.tools.jib.gradle.JibTask

val dockerImagePrefix: String by project
val dockerImageTag: String by project

plugins {
    // Apply precompiled plugins.
    id("ort-server-kotlin-jvm-conventions")
    id("ort-server-publication-conventions")

    // Apply third-party plugins.
    alias(libs.plugins.jib)
}

group = "org.eclipse.apoapsis.ortserver"

tasks.withType<JibTask> {
    notCompatibleWithConfigurationCache("https://github.com/GoogleContainerTools/jib/issues/3132")
}

dependencies {
    implementation(projects.config.configSpi)
    implementation(projects.dao)
    implementation(projects.utils.logging)
    implementation(projects.services.hierarchyService)
    implementation(projects.services.ortRunService)
    implementation(projects.services.reportStorageService)
    implementation(projects.storage.storageSpi)
    implementation(projects.transport.transportSpi)

    implementation(libs.koinCore)
    implementation(libs.kotlinxCoroutines)
    implementation(libs.kubernetesClient)
    implementation(libs.logback)
    implementation(libs.ortDownloader)
    implementation(libs.ortScanner)
    implementation(libs.typesafeConfig)

    runtimeOnly(platform(projects.storage))
    runtimeOnly(platform(projects.config))
    runtimeOnly(platform(projects.transport))

    testImplementation(testFixtures(projects.dao))
    testImplementation(testFixtures(projects.transport.transportSpi))

    testImplementation(libs.koinTest)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.mockk)
}

jib {
    from.image = "eclipse-temurin:${libs.versions.eclipseTemurin.get()}"
    to.image = "${dockerImagePrefix}ort-server-maintenance-tasks:$dockerImageTag"

    container {
        mainClass = "org.eclipse.apoapsis.ortserver.tasks.TaskRunnerKt"
        creationTime.set("USE_CURRENT_TIMESTAMP")
    }
}
