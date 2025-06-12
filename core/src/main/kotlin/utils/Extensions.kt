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

package org.eclipse.apoapsis.ortserver.core.utils

import io.ktor.server.application.ApplicationCall

import org.eclipse.apoapsis.ortserver.clients.keycloak.KeycloakClientConfiguration
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters.Companion.DEFAULT_LIMIT
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagingOptions
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortProperty

/**
 * Return the numeric value of the parameter with the given [name]. Throw a [QueryParametersException] if a value
 * is provided which cannot be converted to a number.
 */
fun ApplicationCall.numberParameter(name: String): Number? =
    try {
        parameters[name]?.toLong()
    } catch (e: NumberFormatException) {
        throw QueryParametersException(
            "Invalid value for parameter '$name': Expected a number, was '${parameters[name]}'.",
            e
        )
    }

fun ConfigManager.createKeycloakClientConfiguration() =
    KeycloakClientConfiguration(
        apiUrl = getString("keycloak.apiUrl"),
        clientId = getString("keycloak.clientId"),
        accessTokenUrl = getString("keycloak.accessTokenUrl"),
        apiUser = getString("keycloak.apiUser"),
        apiSecret = getSecret(Path("keycloak.apiSecret")),
        subjectClientId = getString("keycloak.subjectClientId")
    )

/**
 * Return a [PagingOptions] object for this [ApplicationCall]. If no limit is provided, [DEFAULT_LIMIT] is ues. If no
 * offset is provided, 0 is used. If no sort order is provided, the [defaultSortProperty] is used.
 *
 * The default values ensure that reproducible results are returned and that large numbers of results are avoided.
 */
fun ApplicationCall.pagingOptions(defaultSortProperty: SortProperty): PagingOptions {
    val sortProperties = parameters["sort"]?.let(::processSortParameter).orEmpty().takeIf { it.isNotEmpty() }
        ?: listOf(defaultSortProperty)
    val limit = numberParameter("limit")?.toInt()?.takeIf { it > 0 } ?: DEFAULT_LIMIT
    val offset = numberParameter("offset")?.toLong()?.takeIf { it >= 0 } ?: 0

    return PagingOptions(limit, offset, sortProperties)
}

/**
 * Converts the given [sort] parameter with the properties to sort request results to a list of [SortProperty] objects.
 * The parameter is expected to contain a comma-separated list of property names. To define the sort direction for each
 * property, it can have one of the prefixes "+" for ascending or "-" for descending. If no prefix is provided,
 * ascending is assumed.
 */
private fun processSortParameter(sort: String): List<SortProperty> {
    val fields = sort.split(',')

    return fields.map(String::toSortProperty)
}

/** A map to associate sort direction prefixes with the corresponding constants. */
private val sortPrefixes = mapOf(
    '+' to SortDirection.ASCENDING,
    '-' to SortDirection.DESCENDING
)

/**
 * Convert this string to a [SortProperty]. The string is expected to contain a property name with an optional prefix
 * determining the sort order.
 */
private fun String.toSortProperty(): SortProperty {
    val directionFromPrefix = sortPrefixes.filterKeys { prefix -> startsWith(prefix) }.map { it.value }.firstOrNull()
    return directionFromPrefix?.let { SortProperty(substring(1), directionFromPrefix) }
        ?: SortProperty(this, SortDirection.ASCENDING)
}

/**
 * Paginate this list based on the given [pagingOptions]. If no offset is provided, 0 is used. If no limit is provided,
 * all elements are returned.
 */
fun <T> List<T>.paginate(pagingOptions: PagingOptions): List<T> {
    val offset = pagingOptions.offset ?: 0L
    val limit = pagingOptions.limit ?: Integer.MAX_VALUE

    return drop(offset.toInt()).take(limit)
}
