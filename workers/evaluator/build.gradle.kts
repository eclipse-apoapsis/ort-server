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
val dockerBaseImagePrefix: String by project
val dockerBaseImageTag: String by project

plugins {
    // Apply core plugins.
    application

    // Apply precompiled plugins.
    id("ort-server-kotlin-jvm-conventions")
    id("ort-server-publication-conventions")

    // Apply third-party plugins.
    alias(libs.plugins.jib)
}

group = "org.eclipse.apoapsis.ortserver.workers"

tasks.withType<JibTask> {
    notCompatibleWithConfigurationCache("https://github.com/GoogleContainerTools/jib/issues/3132")
}

dependencies {
    implementation(projects.config.configSpi)
    implementation(projects.dao)
    implementation(projects.model)
    implementation(projects.services.adminConfigService)
    implementation(projects.transport.transportSpi)
    implementation(projects.utils.logging)
    implementation(projects.workers.common)

    implementation(libs.typesafeConfig)
    implementation(ortLibs.utils.config)
    implementation(ortLibs.evaluator)
    implementation(platform(ortLibs.ortPlugins.packageConfigurationProviders))

    runtimeOnly(platform(projects.config))
    runtimeOnly(platform(projects.storage))
    runtimeOnly(platform(projects.transport))

    runtimeOnly(libs.log4jToSlf4j)
    runtimeOnly(libs.logback)

    testImplementation(projects.shared.ortTestData)

    testImplementation(testFixtures(projects.config.configSpi))
    testImplementation(testFixtures(projects.dao))
    testImplementation(testFixtures(projects.transport.transportSpi))

    testImplementation(libs.koinTest)
    testImplementation(libs.kotestAssertionsCore)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.mockk)
}

jib {
    from.image = "${dockerBaseImagePrefix}ort-server-evaluator-worker-base-image:$dockerBaseImageTag"
    to.image = "${dockerImagePrefix}ort-server-evaluator-worker:$dockerImageTag"

    container {
        mainClass = "org.eclipse.apoapsis.ortserver.workers.evaluator.EntrypointKt"
        creationTime = "USE_CURRENT_TIMESTAMP"

        if (System.getProperty("idea.active").toBoolean()) {
            jvmFlags = listOf("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5060")
        }
    }
}
