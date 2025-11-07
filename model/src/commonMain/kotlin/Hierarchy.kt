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

package org.eclipse.apoapsis.ortserver.model

/**
 * A data class that stores the information about the hierarchy of a [Repository].
 *
 * Due to the hierarchical organization of the ORT Server data model, it is required for some use cases to obtain the
 * entities a [Repository] to be processed belongs to. For instance, it is possible that an infrastructure service
 * referenced by the repository is defined on the product or organization level.
 *
 * An instance of this class allows convenient access to this information. It can be obtained from the repository for
 * repositories.
 */
data class Hierarchy(
    /** The current [Repository]. */
    val repository: Repository,

    /** The [Product] the current repository belongs to. */
    val product: Product,

    /** The [Organization] the current repository and product belong to. */
    val organization: Organization
) {
    val compoundId: CompoundHierarchyId by lazy {
        CompoundHierarchyId.forRepository(
            organizationId = OrganizationId(organization.id),
            productId = ProductId(product.id),
            repositoryId = RepositoryId(repository.id)
        )
    }
}
