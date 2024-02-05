/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

@Suppress("DSL_SCOPE_VIOLATION") // See https://youtrack.jetbrains.com/issue/KTIJ-19369.
plugins {
    alias(libs.plugins.dependencyAnalysis)
    alias(libs.plugins.detekt)
    alias(libs.plugins.gitSemver)
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.versionCatalogUpdate)
    alias(libs.plugins.versions)
}

semver {
    // Do not create an empty release commit when running the "releaseVersion" task.
    createReleaseCommit = false

    // Do not let untracked files bump the version or add a "-SNAPSHOT" suffix.
    noDirtyCheck = true
}

version = semver.semVersion

logger.lifecycle("Building ORT Server version $version.")

versionCatalogUpdate {
    // Keep the custom sorting / grouping.
    sortByKey.set(false)
}

allprojects {
    buildscript {
        repositories {
            mavenCentral()
        }
    }

    repositories {
        mavenCentral()

        exclusiveContent {
            forRepository {
                maven("https://jitpack.io")
            }
            filter {
                includeGroup("com.github.oss-review-toolkit.ort")
                includeModule("com.github.Ricky12Awesome", "json-schema-serialization")
            }
        }
    }

    tasks.withType<Jar> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true

        manifest {
            attributes["Implementation-Version"] = version
        }
    }
}

subprojects {
    version = rootProject.version

    apply(plugin = "io.gitlab.arturbosch.detekt")

    dependencies {
        "detektPlugins"("io.gitlab.arturbosch.detekt:detekt-formatting:${rootProject.libs.versions.detektPlugin.get()}")

        "detektPlugins"("org.ossreviewtoolkit:detekt-rules:${rootProject.libs.versions.ort.get()}")
    }

    detekt {
        // Only configure differences to the default.
        buildUponDefaultConfig = true
        config.setFrom(files("$rootDir/.detekt.yml"))
        basePath = rootProject.projectDir.path
        source.from(fileTree(".") { include("*.gradle.kts") }, "src/testFixtures/kotlin")
    }

    val javaVersion = JavaVersion.current()
    val maxKotlinJvmTarget = runCatching { JvmTarget.fromTarget(javaVersion.majorVersion) }
        .getOrDefault(enumValues<JvmTarget>().max())

    tasks.withType<JavaCompile>().configureEach {
        // Align this with Kotlin to avoid errors, see https://youtrack.jetbrains.com/issue/KT-48745.
        sourceCompatibility = maxKotlinJvmTarget.target
        targetCompatibility = maxKotlinJvmTarget.target
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget = maxKotlinJvmTarget
        }

        kotlinOptions {
            allWarningsAsErrors = true
            apiVersion = "1.8"
        }
    }

    tasks.withType<Test>().configureEach {
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
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates").configure {
    gradleReleaseChannel = "current"
    outputFormatter = "json"

    val nonFinalQualifiers = listOf(
        "alpha", "b", "beta", "cr", "dev", "ea", "eap", "m", "milestone", "pr", "preview", "rc", "\\d{14}"
    ).joinToString("|", "(", ")")

    val nonFinalQualifiersRegex = Regex(".*[.-]$nonFinalQualifiers[.\\d-+]*", RegexOption.IGNORE_CASE)

    rejectVersionIf {
        candidate.version.matches(nonFinalQualifiersRegex)
    }
}
