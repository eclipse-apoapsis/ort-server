/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.core.utils

import io.ktor.server.application.ApplicationCall

import org.ossreviewtoolkit.server.clients.keycloak.KeycloakClientConfiguration
import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.config.Path
import org.ossreviewtoolkit.server.dao.QueryParametersException
import org.ossreviewtoolkit.server.model.util.ListQueryParameters
import org.ossreviewtoolkit.server.model.util.OrderDirection
import org.ossreviewtoolkit.server.model.util.OrderField

/**
 * Get the parameter from this [ApplicationCall].
 */
fun ApplicationCall.requireParameter(name: String) = requireNotNull(parameters[name]) {
    "Parameter '$name' cannot be null."
}

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
    with(getConfig("keycloak")) {
        KeycloakClientConfiguration(
            apiUrl = getString("apiUrl"),
            clientId = getString("clientId"),
            accessTokenUrl = getString("accessTokenUrl"),
            apiUser = getString("apiUser"),
            apiSecret = getSecret(Path("keycloakApiSecret")),
            subjectClientId = getString("subjectClientId")
        )
    }

/**
 * Return a [ListQueryParameters] object with the standard query parameters defined for this [ApplicationCall]. This
 * can then be used when calling services.
 */
fun ApplicationCall.listQueryParameters(): ListQueryParameters =
    ListQueryParameters(
        limit = numberParameter("limit")?.toInt(),
        offset = numberParameter("offset")?.toLong(),
        sortFields = parameters["sort"]?.let(::processSortParameter).orEmpty()
    )

/**
 * Converts the given [sort] parameter with the fields to sort query results to a list of [OrderField] objects. The
 * parameter is expected to contain a comma-separated list of field names. To define the sort order for each field, it
 * can have one of the prefixes "+" for ascending or "-" for descending. If no prefix is provided, ascending is
 * assumed.
 */
private fun processSortParameter(sort: String): List<OrderField> {
    val fields = sort.split(',')

    return fields.map(String::toOrderField)
}

/** A map to associate sort order prefixes with the corresponding constants. */
private val orderPrefixes = mapOf(
    '+' to OrderDirection.ASCENDING,
    '-' to OrderDirection.DESCENDING
)

/**
 * Convert this string to an [OrderField]. The string is expected to contain a field name with an option prefix
 * determining the sort order.
 */
private fun String.toOrderField(): OrderField {
    val orderFromPrefix = orderPrefixes.filterKeys { prefix -> startsWith(prefix) }.map { it.value }.firstOrNull()
    return orderFromPrefix?.let { OrderField(substring(1), orderFromPrefix) }
        ?: OrderField(this, OrderDirection.ASCENDING)
}
