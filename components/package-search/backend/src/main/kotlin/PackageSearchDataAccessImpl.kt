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

package org.eclipse.apoapsis.ortserver.components.packagesearch.backend

import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository

class PackageSearchDataAccessImpl(
    private val ortRunRepository: OrtRunRepository
) : PackageSearchDataAccess {
    override fun findOrtRunsByPackage(
        identifier: String,
        organizationId: Long?,
        productId: Long?,
        repositoryId: Long?
    ): List<OrtRunResult> {
        return ortRunRepository.findOrtRunsByPackage(identifier, organizationId, productId, repositoryId)
            .map {
                OrtRunResult(
                    organizationId = it.ortRun.organizationId,
                    productId = it.ortRun.productId,
                    repositoryId = it.ortRun.repositoryId,
                    id = it.ortRun.id,
                    revision = it.ortRun.revision,
                    createdAt = it.ortRun.createdAt,
                    packageId = it.packageId
                )
            }
    }
}
