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

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.shadow)
}

group = "org.eclipse.apoapsis.ortserver.utils"

dependencies {
    implementation(libs.kaml)

    testImplementation(projects.api.v1.apiV1Model)

    testImplementation(libs.kotestAssertionsCore)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotlinxSerializationJson)
}

// Use the shadow plugin to move conflicting packages to a new package structure.
val shadowTask = tasks.named<ShadowJar>("shadowJar") {
    relocate("org.snakeyaml", "org.eclipse.apoapsis.ortserver.utils.snakeyaml")
    archiveClassifier = ""
}

// Disable the default JAR task, so that the shadow JAR is the only artifact.
tasks.named("jar").configure {
    enabled = false
}

tasks.named("build").configure {
    dependsOn("shadowJar")
}

// Make sure that the shadow jar is published.
publishing {
    publications {
        withType<MavenPublication> {
            setArtifacts(listOf(shadowTask.get().archiveFile))
        }
    }
}
