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

plugins {
    // Apply precompiled plugins.
    id("ort-server-kotlin-multiplatform-conventions")
    id("ort-server-publication-conventions")

    // Apply third-party plugins.
    alias(libs.plugins.kotlinSerialization)
}

group = "org.eclipse.apoapsis.ortserver.api.v1"

kotlin {
    jvm()

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.api.v1.apiV1Model)
                implementation(libs.ktorClientAuth)
                implementation(libs.ktorClientContentNegotiation)
                implementation(libs.ktorKotlinxSerializationMP)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotestAssertionsCore)
                implementation(libs.ktorClientMock)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.ktorClientOkHttp)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotestRunnerJunit5)
            }
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()

    filter {
        isFailOnNoMatchingTests = false
    }

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
