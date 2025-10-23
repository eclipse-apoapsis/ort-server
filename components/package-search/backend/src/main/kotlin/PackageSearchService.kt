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

import org.eclipse.apoapsis.ortserver.components.packagesearch.apimodel.PackageSearchDto

// Data access interface for dependency injection and mocking
interface PackageSearchDataAccess {
    fun findOrtRunsByPackage(
        identifier: String,
        organizationId: Long? = null,
        productId: Long? = null,
        repositoryId: Long? = null
    ): List<OrtRunResult>
}

// Minimal result type for mapping to DTO (could be replaced with actual OrtRun model)
data class OrtRunResult(
    val organizationId: Long,
    val productId: Long,
    val repositoryId: Long,
    val id: Long,
    val revision: String?,
    val createdAt: kotlinx.datetime.Instant,
    val packageId: String,
)

class PackageSearchService(private val dataAccess: PackageSearchDataAccess) {
    /**
     * Search for scan runs containing the given package identifier, with optional scoping.
     * Throws IllegalArgumentException for invalid scoping hierarchy or identifier.
     */
    fun search(
        identifier: String?,
        organizationId: Long? = null,
        productId: Long? = null,
        repositoryId: Long? = null
    ): List<PackageSearchDto> {
        requireNotNull(identifier) { "Package identifier must be provided." }
        if (productId != null) requireNotNull(organizationId) { "productId requires organizationId." }
        if (repositoryId != null) {
            require(organizationId != null && productId != null) {
                "repositoryId requires organizationId and productId."
            }
        }

        val runs = dataAccess.findOrtRunsByPackage(identifier, organizationId, productId, repositoryId)
        return runs.map {
            PackageSearchDto(
                organizationId = it.organizationId,
                productId = it.productId,
                repositoryId = it.repositoryId,
                ortRunId = it.id,
                revision = it.revision,
                createdAt = it.createdAt,
                packageId = it.packageId
            )
        }
    }
}
