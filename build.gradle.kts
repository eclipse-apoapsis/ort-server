/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

val dockerBaseBuildArgs: String by project
val dockerBaseImageTag: String by project

plugins {
    alias(libs.plugins.dependencyAnalysis)
    alias(libs.plugins.gitSemver)
    alias(libs.plugins.jib) apply false
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.versions)
}

semver {
    // Do not create an empty release commit when running the "releaseVersion" task.
    createReleaseCommit = false

    // Do not let untracked files bump the version or add a "-SNAPSHOT" suffix.
    noDirtyCheck = true
}

// Only override a default version (which usually is "unspecified"), but not a custom version.
if (version == Project.DEFAULT_VERSION) {
    version = semver.semVersion.takeIf { it.isPreRelease }
        // To get rid of a build part's "+" prefix because Docker tags do not support it, use only the original "build"
        // part as the "pre-release" part.
        ?.toString()?.replace("${semver.defaultPreRelease}+", "")
        // Fall back to a plain version without pre-release or build parts.
        ?: semver.version
}

logger.lifecycle("Building ORT Server version $version.")

allprojects {
    apply(plugin = rootProject.libs.plugins.mavenPublish.get().pluginId)

    mavenPublishing {
        pom {
            name = project.name
            description = "Part of the ORT Server, the reference implementation of Eclipse Apoapsis."
            url = "https://projects.eclipse.org/projects/technology.apoapsis"

            developers {
                developer {
                    name = "The ORT Server Project Authors"
                }
            }

            licenses {
                license {
                    name = "Apache-2.0"
                    url = "https://www.apache.org/licenses/LICENSE-2.0"
                }
            }
        }

        publishing {
            repositories {
                maven {
                    name = "githubPackages"

                    // The owner and repository need to be configured in the `GITHUB_REPOSITORY` environment variable,
                    // for example "octocat/Hello-Word". This variable is set by default in GitHub actions, see
                    // https://docs.github.com/en/actions/learn-github-actions/variables#default-environment-variables.
                    url = uri("https://maven.pkg.github.com/${System.getenv("GITHUB_REPOSITORY")}")

                    // Username and password (a personal GitHub access token) should be specified as
                    // `githubPackagesUsername` and `githubPackagesPassword` Gradle properties or alternatively as
                    // `ORG_GRADLE_PROJECT_githubPackagesUsername` and `ORG_GRADLE_PROJECT_githubPackagesPassword`
                    // environment variables.
                    credentials(PasswordCredentials::class)
                }
            }
        }
    }
}

subprojects {
    val javaVersion = JavaVersion.current()
    val maxKotlinJvmTarget = runCatching { JvmTarget.fromTarget(javaVersion.majorVersion) }
        .getOrDefault(enumValues<JvmTarget>().max())

    tasks.withType<JavaCompile>().configureEach {
        // Align this with Kotlin to avoid errors, see https://youtrack.jetbrains.com/issue/KT-48745.
        sourceCompatibility = maxKotlinJvmTarget.target
        targetCompatibility = maxKotlinJvmTarget.target
    }

    tasks.withType<KotlinCompile>().configureEach {
        val hasSerializationPlugin = plugins.hasPlugin(libs.plugins.kotlinSerialization.get().pluginId)

        compilerOptions {
            allWarningsAsErrors = true
            jvmTarget = maxKotlinJvmTarget
            if (hasSerializationPlugin) optIn = listOf("kotlinx.serialization.ExperimentalSerializationApi")
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

rootDir.walk().maxDepth(4).filter { it.isFile && it.extension == "Dockerfile" }.forEach { dockerfile ->
    val name = dockerfile.name.substringBeforeLast('.')

    val buildArgs = dockerBaseBuildArgs.takeUnless { it.isBlank() }
        ?.split(',')
        ?.flatMap { listOf("--build-arg", it.trim()) }
        .orEmpty()

    if (name == "UI") {
        tasks.register<Exec>("build${name}Image") {
            val context = dockerfile.parentFile.parent

            dependsOn(":core:generateOpenApiSpec")

            group = "Docker"
            description = "Builds the $name Docker image."

            inputs.file(dockerfile)
            inputs.dir(context)

            commandLine = listOf(
                "docker", "build",
                "-f", dockerfile.path,
                *buildArgs.toTypedArray(),
                "-t", "ort-server-${name.lowercase()}:$dockerBaseImageTag",
                "-q",
                context
            )
        }
    } else {
        tasks.register<Exec>("build${name}WorkerImage") {
            val context = dockerfile.parent

            group = "Docker"
            description = "Builds the $name worker Docker image."

            inputs.file(dockerfile)
            inputs.dir(context)

            commandLine = listOf(
                "docker", "build",
                "-f", dockerfile.path,
                *buildArgs.toTypedArray(),
                "-t", "ort-server-${name.lowercase()}-worker-base-image:$dockerBaseImageTag",
                "-q",
                context
            )
        }
    }
}

val buildAllWorkerImages by tasks.registering {
    val workerImageTaskRegex = Regex("build[A-Z][a-z]+WorkerImage")
    val workerImageTasks = tasks.matching { it.name.matches(workerImageTaskRegex) }
    dependsOn(workerImageTasks)
}

tasks.register("buildAllImages") {
    val jibDockerBuilds = getTasksByName("jibDockerBuild", /* recursive = */ true).onEach {
        it.mustRunAfter(buildAllWorkerImages)
    }

    val uiDockerBuild = getTasksByName("buildUIImage", /* recursive = */ false).single()

    dependsOn(buildAllWorkerImages, jibDockerBuilds, uiDockerBuild)
}
