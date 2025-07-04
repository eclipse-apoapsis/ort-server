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

import io.gitlab.arturbosch.detekt.Detekt

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.kotlin.dsl.dependencies

private val Project.libs: LibrariesForLibs
    get() = extensions.getByType()

plugins {
    // Apply precompiled plugins.
    id("ort-server-base-conventions")

    // Apply third-party plugins.
    id("io.gitlab.arturbosch.detekt")
}

configurations.all {
    resolutionStrategy {
        // Required until the AWS SDK for Kotlin is updated to use the stable release of OkHttp.
        force("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    }
}

dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:${rootProject.libs.versions.detektPlugin.get()}")
    detektPlugins("org.ossreviewtoolkit:detekt-rules:${rootProject.libs.versions.ort.get()}")
}

detekt {
    // Only configure differences to the default.
    buildUponDefaultConfig = true
    config.from(files("$rootDir/.detekt.yml"))

    source.from(
        fileTree(".") { include("*.gradle.kts") },
        "src/commonMain/kotlin",
        "src/commonTest/kotlin",
        "src/testFixtures/kotlin"
    )

    basePath = rootDir.path
}

tasks.withType<Detekt>().configureEach {
    exclude {
        "/build/generated/" in it.file.absolutePath
    }
}

tasks.register("detektAll") {
    group = "Verification"
    description = "Run all detekt tasks with type resolution."

    dependsOn(tasks.withType<Detekt>().filterNot { it.name == "detekt" })
}
