/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

val dockerImagePrefix: String by project
val dockerImageTag: String by project

plugins {
    application

    alias(libs.plugins.jib)
    alias(libs.plugins.kotlinJvm)
}

group = "org.ossreviewtoolkit.server.workers"
version = "0.0.1"

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    implementation(project(":dao"))
    implementation(project(":model"))
    implementation(project(":transport:transport-spi"))
    implementation(project(":workers:common"))

    implementation(libs.ortScanner)
    implementation(libs.typesafeConfig)

    runtimeOnly(project(":secrets:file"))
    runtimeOnly(project(":secrets:vault"))
    runtimeOnly(project(":transport:activemqartemis"))
    runtimeOnly(project(":transport:kubernetes"))
    runtimeOnly(project(":transport:rabbitmq"))

    runtimeOnly(libs.logback)

    testImplementation(testFixtures(project(":dao")))
    testImplementation(testFixtures(project(":transport:transport-spi")))
    testImplementation(libs.koinTest)
    testImplementation(libs.kotestAssertionsCore)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.mockk)
}

repositories {
    exclusiveContent {
        forRepository {
            maven("https://repo.gradle.org/gradle/libs-releases/")
        }

        filter {
            includeGroup("org.gradle")
        }
    }

    exclusiveContent {
        forRepository {
            maven("https://repo.eclipse.org/content/repositories/sw360-releases/")
        }

        filter {
            includeGroup("org.eclipse.sw360")
        }
    }
}

jib {
    from.image = "docker://ort-server-scanner-worker-base-image:latest"
    to.image = "${dockerImagePrefix}ort-server-scanner-worker:$dockerImageTag"

    container {
        mainClass = "org.ossreviewtoolkit.server.workers.scanner.EntrypointKt"
        creationTime.set("USE_CURRENT_TIMESTAMP")
    }
}
