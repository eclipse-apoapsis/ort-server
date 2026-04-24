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

import dev.detekt.gradle.Detekt

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

private val catalogs = extensions.getByType<VersionCatalogsExtension>()
private val detektRulesVersion = catalogs.named("ortLibs").findLibrary("detektRules").get().get().version

plugins {
    // Apply precompiled plugins.
    id("ort-server-base-conventions")

    // Apply third-party plugins.
    id("dev.detekt")
}

dependencies {
    detektPlugins("dev.detekt:detekt-rules-ktlint-wrapper:${libs.versions.detektPlugin.get()}")
    detektPlugins("org.ossreviewtoolkit:detekt-rules:$detektRulesVersion")
}

detekt {
    // Only configure differences to the default.
    buildUponDefaultConfig = true
    config.from(files("$rootDir/.detekt.yml"))

    source.from(
        fileTree(".") { include("*.gradle.kts") },
        "src/commonMain/kotlin",
        "src/commonTest/kotlin",
        "src/routes/kotlin",
        "src/testFixtures/kotlin"
    )

    basePath = rootDir
}

tasks.withType<KotlinCompile>().configureEach {
    val serializationOptIn = libraries.elements.map { files ->
        buildList {
            if (files.any { it.asFile.name.startsWith("kotlinx-serialization-core") }) {
                add("kotlinx.serialization.ExperimentalSerializationApi")
            }
        }
    }

    compilerOptions {
        allWarningsAsErrors = true
        freeCompilerArgs.addAll("-Xconsistent-data-class-copy-visibility")
        optIn = serializationOptIn
    }
}

tasks.withType<Detekt>().configureEach {
    exclude {
        "/build/generated/" in it.file.absoluteFile.invariantSeparatorsPath
    }

    reports {
        // Disable these as they have issues with Gradle task output caching due to contained timestamps.
        html.required = false
        markdown.required = false

        sarif.required = true
    }
}

tasks.register("detektAll") {
    group = "Verification"
    description = "Run all detekt tasks with type resolution."

    dependsOn(tasks.withType<Detekt>().filterNot { it.name == "detekt" })
}
