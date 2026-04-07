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

package org.eclipse.apoapsis.ortserver.components.licensefindings.routes

import io.ktor.server.application.ApplicationCall

import org.eclipse.apoapsis.ortserver.components.authorization.rights.EffectiveRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.HierarchyPermissions
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.routes.AuthorizationChecker
import org.eclipse.apoapsis.ortserver.components.authorization.service.AuthorizationService
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter

fun requireRunReadPermission(ortRunRepository: OrtRunRepository): AuthorizationChecker =
    object : AuthorizationChecker {
        override suspend fun loadEffectiveRole(
            service: AuthorizationService,
            userId: String,
            call: ApplicationCall
        ): EffectiveRole? {
            val runId = call.requireIdParameter("runId")
            val ortRun = ortRunRepository.get(runId) ?: return null

            return service.checkPermissions(
                userId,
                RepositoryId(ortRun.repositoryId),
                HierarchyPermissions.permissions(RepositoryPermission.READ_ORT_RUNS)
            )
        }
    }
