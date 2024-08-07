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

import org.gradle.accessors.dm.LibrariesForLibs

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

private val Project.libs: LibrariesForLibs
    get() = extensions.getByType()

plugins {
    // Apply precompiled plugins.
    id("ort-server-kotlin-conventions")

    // Apply third-party plugins.
    id("org.jetbrains.kotlin.jvm")
}

val javaVersion = JavaVersion.current()
val maxKotlinJvmTarget = runCatching { JvmTarget.fromTarget(javaVersion.majorVersion) }
    .getOrDefault(enumValues<JvmTarget>().max())

tasks.withType<JavaCompile>().configureEach {
    // Align this with Kotlin to avoid errors, see https://youtrack.jetbrains.com/issue/KT-48745.
    sourceCompatibility = maxKotlinJvmTarget.target
    targetCompatibility = maxKotlinJvmTarget.target
}

tasks.named<KotlinCompile>("compileKotlin") {
    val hasSerializationPlugin = plugins.hasPlugin(libs.plugins.kotlinSerialization.get().pluginId)

    val optInRequirements = listOfNotNull(
        "kotlinx.serialization.ExperimentalSerializationApi".takeIf { hasSerializationPlugin }
    )

    compilerOptions {
        allWarningsAsErrors = true
        jvmTarget = maxKotlinJvmTarget
        optIn = optInRequirements
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    // Required since Java 17, see: https://kotest.io/docs/next/extensions/system_extensions.html#system-environment
    if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17)) {
        jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
    }

    val testSystemProperties = mutableListOf("gradle.build.dir" to project.layout.buildDirectory.get().toString())

    listOf(
        "kotest.assertions.multi-line-diff",
        "kotest.tags"
    ).mapNotNullTo(testSystemProperties) { key ->
        System.getProperty(key)?.let { key to it }
    }

    systemProperties = testSystemProperties.toMap()

    testLogging {
        events = setOf(
            org.gradle.api.tasks.testing.logging.TestLogEvent.STARTED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
        )
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}
