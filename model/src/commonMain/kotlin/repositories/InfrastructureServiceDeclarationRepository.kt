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

package org.eclipse.apoapsis.ortserver.model.repositories

import org.eclipse.apoapsis.ortserver.model.InfrastructureServiceDeclaration

/**
 * Repository interface to manage [InfrastructureServiceDeclaration] entities.
 */
interface InfrastructureServiceDeclarationRepository {
    /**
     * Return an [InfrastructureServiceDeclaration] with properties matching the ones
     * of the given [service] that is associated with the given [ORT Run][runId]. Try to find an already existing
     * [service] with the given properties first and return this. If not found, create a new
     * [InfrastructureServiceDeclaration]. In both cases, associate the [service] with the given [ORT Run][runId].
     */
    fun getOrCreateForRun(service: InfrastructureServiceDeclaration, runId: Long): InfrastructureServiceDeclaration

    /**
     * Return the [InfrastructureServiceDeclaration]s associated to the given [ORT Run][runId].
     */
    fun listForRun(runId: Long): List<InfrastructureServiceDeclaration>
}
