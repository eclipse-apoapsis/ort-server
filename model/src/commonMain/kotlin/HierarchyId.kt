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

package org.eclipse.apoapsis.ortserver.model

/**
 * A sealed interface for a [value] that describes an ID within a specific hierarchy.
 */
sealed interface HierarchyId {
    val value: Long
}

/**
 * An ID for an [Organization].
 */
@JvmInline
value class OrganizationId(override val value: Long) : HierarchyId

/**
 * An ID for a [Product].
 */
@JvmInline
value class ProductId(override val value: Long) : HierarchyId

/**
 * An ID for a [Repository].
 */
@JvmInline
value class RepositoryId(override val value: Long) : HierarchyId

/**
 * A class storing all IDs in the hierarchy for a specific element. This is helpful for certain use cases which
 * require easy access to the parent levels an element belongs to. One example is permission management where
 * permissions defined on higher levels can be inherited to lower levels.
 *
 * IDs irrelevant for the current level are represented as *null* values. For instance, if the identified element is a
 * product, the [repositoryId] is set to *null*.
 *
 * The class provides some functionality to access parent structures and to check whether an element is part of the
 * hierarchy of another element.
 */
data class CompoundHierarchyId private constructor(
    /**
     * The [OrganizationId] this element belongs to. This is only *null* for the special [WILDCARD] instance.
     */
    val organizationId: OrganizationId?,

    /** The [ProductId] this element belongs to or *null* if the element is an organization. */
    val productId: ProductId?,

    /** The [RepositoryId] this element belongs to or *null* if the element is an organization or product. */
    val repositoryId: RepositoryId?
) {
    companion object {
        /**
         * A special instance representing a wildcard matching any hierarchy ID.
         */
        val WILDCARD = CompoundHierarchyId(null, null, null)

        /** The level representing an organization. */
        const val ORGANIZATION_LEVEL = 1

        /** The level representing a product. */
        const val PRODUCT_LEVEL = 2

        /** The level representing a repository. */
        const val REPOSITORY_LEVEL = 3

        /**
         * A special level constant representing the [WILDCARD] instance. This ID does not belong to any hierarchy
         * level.
         */
        const val WILDCARD_LEVEL = 0

        /**
         * Create a [CompoundHierarchyId] for an organization.
         */
        fun forOrganization(organizationId: OrganizationId) =
            CompoundHierarchyId(organizationId, null, null)

        /**
         * Create a [CompoundHierarchyId] for a product.
         */
        fun forProduct(organizationId: OrganizationId, productId: ProductId) =
            CompoundHierarchyId(organizationId, productId, null)

        /**
         * Create a [CompoundHierarchyId] for a repository.
         */
        fun forRepository(
            organizationId: OrganizationId,
            productId: ProductId,
            repositoryId: RepositoryId
        ) = CompoundHierarchyId(organizationId, productId, repositoryId)
    }

    /**
     * The parent [CompoundHierarchyId] of this instance. This is the ID referencing the next higher level in the
     * hierarchy. For an organization this is *null*.
     */
    val parent: CompoundHierarchyId?
        get() = when {
            repositoryId != null -> forProduct(organizationId!!, productId!!)
            productId != null -> forOrganization(organizationId!!)
            else -> null
        }

    /**
     * A numeric value representing the level in the hierarchy this ID belongs to. This is one of the _LEVEL_
     * constants.
     */
    val level: Int
        get() = when {
            repositoryId != null -> REPOSITORY_LEVEL
            productId != null -> PRODUCT_LEVEL
            organizationId != null -> ORGANIZATION_LEVEL
            else -> WILDCARD_LEVEL
        }

    /**
     * Return the [HierarchyId] of the specified [level] if it is defined. Throw an [IllegalArgumentException] for
     * invalid level values.
     */
    operator fun get(level: Int): HierarchyId? =
        when (level) {
            ORGANIZATION_LEVEL -> organizationId
            PRODUCT_LEVEL -> productId
            REPOSITORY_LEVEL -> repositoryId
            else -> throw IllegalArgumentException("Invalid level $level")
        }

    /**
     * Check whether the given [other] ID belongs to the hierarchy defined by this ID. Every [CompoundHierarchyId]
     * contains itself. For two different IDs, ID1 contains ID2 if ID1 is on a higher level than ID2 and the IDs on
     * all levels above ID2 are either *null* or equal. The [WILDCARD] instance contains all other IDs.
     */
    operator fun contains(other: CompoundHierarchyId): Boolean =
        level <= other.level && (ORGANIZATION_LEVEL..other.level).all { level ->
            val thisId = this[level]
            thisId == null || thisId == other[level]
        }
}
