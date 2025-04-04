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

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("ort-server-kotlin-multiplatform-conventions")
    id("ort-server-publication-conventions")

    // Apply third-party plugins.
    alias(libs.plugins.kotlinSerialization)
}

group = "org.eclipse.apoapsis.ortserver.cli"

kotlin {
    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        binaries {
            executable {
                mainClass = "org.eclipse.apoapsis.ortserver.cli.OrtServerMainKt"
            }
        }
    }

    linuxX64()
    macosArm64()
    macosX64()
    mingwX64()

    targets.withType<KotlinNativeTarget> {
        binaries {
            executable {
                entryPoint = "org.eclipse.apoapsis.ortserver.cli.main"
                baseName = "osc"
                optimized = true
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.api.v1.apiV1Client)
                implementation(projects.api.v1.apiV1Model)
                implementation(projects.utils.system)

                implementation(libs.clikt)
                implementation(libs.kaml)
                implementation(libs.kotlinxCoroutines)
                implementation(libs.kotlinxSerializationJson)
                implementation(libs.ktorClientAuth)
                implementation(libs.ktorClientCore)
                implementation(libs.ktorUtils)
                implementation(libs.okio)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.ortCommonUtils)
            }
        }

        jvmTest {
            dependencies {
                implementation(projects.api.v1.apiV1Model)

                implementation(libs.kotestAssertionsCore)
                implementation(libs.kotestRunnerJunit5)
                implementation(libs.mockk)
            }
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()

    testLogging {
        events = setOf(
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
        )
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showStandardStreams = true
    }
}
