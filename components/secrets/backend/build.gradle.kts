/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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
    id("ort-server-kotlin-component-backend-conventions")
    id("ort-server-publication-conventions")
}

group = "org.eclipse.apoapsis.ortserver.components.secrets"

dependencies {
    api(projects.model)
    api(projects.secrets.secretsSpi)

    api(libs.exposedCore)

    implementation(projects.dao)

    routesApi(projects.components.secrets.apiModel)

    routesApi(ktorLibs.server.core)
    routesApi(ktorLibs.server.requestValidation)

    routesImplementation(projects.components.authorization.backend)
    routesImplementation(projects.model)
    routesImplementation(projects.services.hierarchyService)
    routesImplementation(projects.shared.apiMappings)
    routesImplementation(projects.shared.apiModel)
    routesImplementation(projects.shared.ktorUtils)

    routesImplementation(libs.ktorOpenApi)

    testImplementation(testFixtures(projects.secrets.secretsSpi))
    testImplementation(testFixtures(projects.shared.ktorUtils))

    testImplementation(ktorLibs.server.statusPages)
    testImplementation(ktorLibs.server.testHost)
    testImplementation(libs.kotestAssertionsCore)
    testImplementation(libs.kotestAssertionsKtor)
    testImplementation(libs.mockk)
}
