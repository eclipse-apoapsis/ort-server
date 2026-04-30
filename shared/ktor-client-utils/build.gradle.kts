/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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
}

group = "org.eclipse.apoapsis.ortserver.shared"

kotlin {
    linuxX64()
    macosArm64()
    @Suppress("deprecation") macosX64()
    mingwX64()

    sourceSets {
        commonMain {
            dependencies {
                api(ktorLibs.client.core)
                implementation(ktorLibs.client.cio)
                implementation(libs.kotlinLogging)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotestAssertionsCore)
                implementation(libs.kotestFrameworkEngine)
            }
        }

        jvmMain {
            dependencies {
                api(libs.typesafeConfig)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotestRunnerJunit5)
                implementation(libs.mockk)
                implementation(libs.wiremock)
            }
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()

    // Required since Java 17, see: https://kotest.io/docs/next/extensions/system_extensions.html#system-environment
    jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
}
