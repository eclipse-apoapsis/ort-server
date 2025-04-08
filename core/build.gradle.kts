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

import com.google.cloud.tools.jib.gradle.JibTask

val dockerImagePrefix: String by project
val dockerImageTag: String by project

plugins {
    // Apply core plugins.
    application

    // Apply precompiled plugins.
    id("ort-server-kotlin-jvm-conventions")
    id("ort-server-publication-conventions")

    // Apply third-party plugins.
    alias(libs.plugins.jib)
    alias(libs.plugins.kotlinSerialization)
}

group = "org.eclipse.apoapsis.ortserver"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")

    val isDevelopment = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

tasks.withType<JibTask> {
    notCompatibleWithConfigurationCache("https://github.com/GoogleContainerTools/jib/issues/3132")
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
}

dependencies {
    implementation(projects.api.v1.apiV1Mapping)
    implementation(projects.clients.keycloak)
    implementation(projects.components.authorization.implementation)
    implementation(projects.components.pluginManager.implementation)
    implementation(projects.config.configSpi)
    implementation(projects.dao)
    implementation(projects.logaccess.logaccessSpi)
    implementation(projects.model)
    implementation(projects.secrets.secretsSpi)
    implementation(projects.services.authorizationService)
    implementation(projects.services.contentManagementService)
    implementation(projects.services.hierarchyService)
    implementation(projects.services.infrastructureService)
    implementation(projects.services.reportStorageService)
    implementation(projects.services.secretService)
    implementation(projects.shared.ktorUtils)
    implementation(projects.storage.storageSpi)
    implementation(projects.transport.transportSpi)
    implementation(projects.utils.logging)
    implementation(projects.utils.system)

    implementation(libs.jsonSchemaSerialization)
    implementation(libs.koinKtor)
    implementation(libs.konform)
    implementation(libs.ktorKotlinxSerialization)
    implementation(libs.ktorOpenApi)
    implementation(libs.ktorServerAuth)
    implementation(libs.ktorServerAuthJwt)
    implementation(libs.ktorServerCallLogging)
    implementation(libs.ktorServerContentNegotiation)
    implementation(libs.ktorServerCore)
    implementation(libs.ktorServerCors)
    implementation(libs.ktorServerDefaultHeaders)
    implementation(libs.ktorServerMetricsMicrometer)
    implementation(libs.ktorServerNetty)
    implementation(libs.ktorServerStatusPages)
    implementation(libs.ktorSwaggerUi)
    implementation(libs.ktorValidation)
    implementation(libs.micrometerRegistryGraphite)
    implementation(libs.ortCommonUtils)
    implementation(libs.ortUtils)
    implementation(libs.bundles.schemaKenerator)

    runtimeOnly(projects.config.secretFile)
    runtimeOnly(platform(projects.logaccess))
    runtimeOnly(platform(projects.secrets))
    runtimeOnly(platform(projects.storage))
    runtimeOnly(platform(projects.transport))

    runtimeOnly(libs.logback)

    testImplementation(testFixtures(projects.clients.keycloak))
    testImplementation(testFixtures(projects.config.configSpi))
    testImplementation(testFixtures(projects.dao))
    testImplementation(testFixtures(projects.logaccess.logaccessSpi))
    testImplementation(testFixtures(projects.secrets.secretsSpi))
    testImplementation(testFixtures(projects.transport.transportSpi))

    testImplementation(libs.kotestAssertionsCore)
    testImplementation(libs.kotestAssertionsKtor)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.ktorClientContentNegotiation)
    testImplementation(libs.ktorClientCore)
    testImplementation(libs.ktorServerCommon)
    testImplementation(libs.ktorServerTestHost)
    testImplementation(libs.ktorUtils)
    testImplementation(libs.mockk)
    testImplementation(libs.ortCommonUtils)
}

jib {
    from.image = "eclipse-temurin:${libs.versions.eclipseTemurin.get()}"
    to.image = "${dockerImagePrefix}ort-server-core:$dockerImageTag"

    container {
        mainClass = "io.ktor.server.netty.EngineMain"
        creationTime.set("USE_CURRENT_TIMESTAMP")
    }
}

/**
 * A fake test task that writes the OpenAPI specification to `build/openapi/openapi.json`.
 */
tasks.register<Test>("generateOpenApiSpec") {
    include("org/eclipse/apoapsis/ortserver/core/utils/GenerateOpenApiSpec.class")
    systemProperties("generateOpenApiSpec" to "true")
}
