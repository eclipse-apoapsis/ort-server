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

package org.ossreviewtoolkit.server.core.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted

import kotlinx.coroutines.runBlocking

import org.koin.ktor.ext.inject

import org.ossreviewtoolkit.server.services.AuthorizationService

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(Application::class.java)

/**
 * Configure actions that are triggered by
 * [lifecycle events][https://ktor.io/docs/events.html#handle-events-application].
 */
fun Application.configureLifecycle() {
    environment.monitor.subscribe(ApplicationStarted) {
        val authorizationService by inject<AuthorizationService>()
        runCatching {
            runBlocking {
                logger.info("Synchronizing Keycloak permissions.")
                authorizationService.synchronizePermissions()
                logger.info("Synchronizing Keycloak roles.")
                authorizationService.synchronizeRoles()
            }
        }.onSuccess {
            logger.info("Synchronized Keycloak permissions and roles.")
        }.onFailure {
            logger.error("Error while synchronizing Keycloak permissions and roles.", it)
        }
    }
}
