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

import com.google.cloud.tools.jib.gradle.JibTask

val dockerImagePrefix: String by project
val dockerImageTag: String by project

plugins {
    application

    alias(libs.plugins.jib)
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinxSerialization)
}

group = "org.eclipse.apoapsis.ortserver.transport"

tasks.withType<JibTask> {
    notCompatibleWithConfigurationCache("https://github.com/GoogleContainerTools/jib/issues/3132")
}

dependencies {
    implementation(projects.dao)
    implementation(projects.model)
    implementation(projects.transport.transportSpi)
    implementation(projects.utils.config)

    implementation(libs.koinCore)
    implementation(libs.kotlinxCoroutines)
    implementation(libs.kotlinxSerializationJson)
    implementation(libs.kubernetesClient)
    implementation(libs.kubernetesClientExtended)
    implementation(libs.typesafeConfig)

    runtimeOnly(projects.transport.activemqartemis)
    runtimeOnly(projects.transport.kubernetes)
    runtimeOnly(projects.transport.rabbitmq)
    runtimeOnly(projects.transport.sqs)

    runtimeOnly(projects.config.secretFile)
    runtimeOnly(libs.logback)

    testImplementation(testFixtures(projects.transport.transportSpi))

    testImplementation(libs.koinTest)
    testImplementation(libs.kotestAssertionsCore)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.mockk)
    testImplementation(libs.ortTestUtils)
}

jib {
    from.image = "eclipse-temurin:${libs.versions.eclipseTemurin.get()}"
    to.image = "${dockerImagePrefix}ort-server-kubernetes-jobmonitor:$dockerImageTag"

    container {
        mainClass = "org.eclipse.apoapsis.ortserver.transport.kubernetes.jobmonitor.EntrypointKt"
        creationTime.set("USE_CURRENT_TIMESTAMP")
    }
}
