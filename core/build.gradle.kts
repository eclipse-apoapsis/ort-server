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
val dockerBaseImagePrefix: String by project
val dockerBaseImageTag: String by project

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
    mainClass = "io.ktor.server.netty.EngineMain"

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
    implementation(projects.components.adminConfig.adminConfigBackend)
    implementation(projects.components.adminConfig.adminConfigBackend) {
        capabilities {
            requireCapability("$group:routes:$version")
        }
    }
    implementation(projects.components.authorization.authorizationBackend) {
        capabilities {
            requireCapability("$group:routes:$version")
        }
    }
    implementation(projects.components.authorizationKeycloak.authorizationKeycloakBackend)
    implementation(projects.components.infrastructureServices.infrastructureServicesBackend)
    implementation(projects.components.infrastructureServices.infrastructureServicesBackend) {
        capabilities {
            requireCapability("$group:routes")
        }
    }
    implementation(projects.components.pluginManager.pluginManagerBackend)
    implementation(projects.components.pluginManager.pluginManagerBackend) {
        capabilities {
            requireCapability("$group:routes:$version")
        }
    }
    implementation(projects.components.search.searchBackend)
    implementation(projects.components.search.searchBackend) {
        capabilities {
            requireCapability("$group:routes:$version")
        }
    }
    implementation(projects.components.secrets.secretsBackend)
    implementation(projects.components.secrets.secretsBackend) {
        capabilities {
            requireCapability("$group:routes:$version")
        }
    }
    implementation(projects.compositions.secretsRoutes)
    implementation(projects.config.configSpi)
    implementation(projects.dao)
    implementation(projects.logaccess.logaccessSpi)
    implementation(projects.model)
    implementation(projects.secrets.secretsSpi)
    implementation(projects.services.contentManagementService)
    implementation(projects.services.hierarchyService)
    implementation(projects.services.ortRunService)
    implementation(projects.services.reportStorageService)
    implementation(projects.shared.apiMappings)
    implementation(projects.shared.apiModel)
    implementation(projects.shared.ktorUtils)
    implementation(projects.shared.reporters)
    implementation(projects.storage.storageSpi)
    implementation(projects.transport.transportSpi)
    implementation(projects.utils.logging)
    implementation(projects.utils.system)

    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.auth.jwt)
    implementation(ktorLibs.server.callLogging)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.cors)
    implementation(ktorLibs.server.defaultHeaders)
    implementation(ktorLibs.server.metrics.micrometer)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.requestValidation)
    implementation(ktorLibs.server.statusPages)
    implementation(libs.bundles.schemaKenerator)
    implementation(libs.koinKtor)
    implementation(libs.konform)
    implementation(libs.ktorOpenApi)
    implementation(libs.ktorSwaggerUi)
    implementation(libs.micrometerRegistryGraphite)
    implementation(ortLibs.utils.common)
    implementation(ortLibs.utils.ort)

    runtimeOnly(projects.config.secretFile)
    runtimeOnly(platform(projects.logaccess))
    runtimeOnly(platform(projects.secrets))
    runtimeOnly(platform(projects.storage))
    runtimeOnly(platform(projects.transport))

    // Dependencies on ORT plugins are required to provide information about them via the API.
    runtimeOnly(platform(ortLibs.ortPlugins.advisors))
    runtimeOnly(platform(ortLibs.ortPlugins.packageConfigurationProviders))
    runtimeOnly(platform(ortLibs.ortPlugins.packageCurationProviders))
    runtimeOnly(platform(ortLibs.ortPlugins.packageManagers))
    runtimeOnly(platform(ortLibs.ortPlugins.reporters))
    runtimeOnly(platform(ortLibs.ortPlugins.scanners))

    runtimeOnly(libs.logback)

    testImplementation(testFixtures(projects.clients.keycloak))
    testImplementation(testFixtures(projects.config.configSpi))
    testImplementation(testFixtures(projects.dao))
    testImplementation(testFixtures(projects.logaccess.logaccessSpi))
    testImplementation(testFixtures(projects.secrets.secretsSpi))
    testImplementation(testFixtures(projects.shared.ktorUtils))
    testImplementation(testFixtures(projects.transport.transportSpi))

    testImplementation(ktorLibs.client.contentNegotiation)
    testImplementation(ktorLibs.client.core)
    testImplementation(ktorLibs.server.testHost)
    testImplementation(ktorLibs.utils)
    testImplementation(libs.kotestAssertionsCore)
    testImplementation(libs.kotestAssertionsKtor)
    testImplementation(libs.kotestAssertionsTable)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.mockk)
    testImplementation(ortLibs.utils.common)
}

jib {
    from.image = "${dockerBaseImagePrefix}ort-server-base-image:$dockerBaseImageTag"
    to.image = "${dockerImagePrefix}ort-server-core:$dockerImageTag"

    container {
        mainClass = "io.ktor.server.netty.EngineMain"
        creationTime = "USE_CURRENT_TIMESTAMP"

        if (System.getProperty("idea.active").toBoolean()) {
            jvmFlags = listOf("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5000")
        }
    }
}

val test by testing.suites.existing(JvmTestSuite::class)

tasks.register<Test>("generateOpenApiSpec") {
    group = "API"
    description = "A fake test task that writes the OpenAPI specification to `ui/build/openapi.json`."
    testClassesDirs = files(test.map { it.sources.output.classesDirs })
    classpath = files(test.map { it.sources.runtimeClasspath })
    include("org/eclipse/apoapsis/ortserver/core/utils/GenerateOpenApiSpec.class")
    systemProperties("generateOpenApiSpec" to "true")
    outputs.file(rootDir.resolve("ui/build/openapi.json"))
}
