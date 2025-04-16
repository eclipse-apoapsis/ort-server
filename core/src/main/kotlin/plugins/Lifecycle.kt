/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.core.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted

import kotlin.concurrent.thread

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import org.eclipse.apoapsis.ortserver.services.AuthorizationService
import org.eclipse.apoapsis.ortserver.utils.logging.runBlocking
import org.eclipse.apoapsis.ortserver.utils.logging.withMdcContext

import org.koin.ktor.ext.inject

import org.slf4j.MDC

/**
 * Configure actions that are triggered by
 * [lifecycle events][https://ktor.io/docs/events.html#handle-events-application].
 */
fun Application.configureLifecycle() {
    monitor.subscribe(ApplicationStarted) {
        val authorizationService by inject<AuthorizationService>()

        val mdcContext = MDC.getCopyOfContextMap()

        thread {
            MDC.setContextMap(mdcContext)
            runBlocking(Dispatchers.IO) {
                syncRoles(authorizationService)
            }
        }
    }
}

/**
 * Trigger the synchronization of permissions and roles in Keycloak. The synchronization then runs in background.
 */
private suspend fun syncRoles(authorizationService: AuthorizationService) {
    withMdcContext("component" to "core") {
        launch {
            authorizationService.ensureSuperuserAndSynchronizeRolesAndPermissions()
        }
    }
}
