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
package org.eclipse.apoapsis.ortserver.core.utils

import io.ktor.http.decodeURLQueryComponent
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.path
import io.ktor.server.request.queryString

/**
 * The names of query parameters whose values are written to the request log. The values of all other query parameters
 * are masked, so that a newly added parameter is never logged by accident.
 */
internal val LOGGED_QUERY_PARAMETERS = setOf(
    "advisors",
    "configContext",
    "declaredLicense",
    "definitionFilePath",
    "externalId",
    "fixAvailable",
    "identifier",
    "identifierMatchType",
    "isDirectDependency",
    "level",
    "license",
    "limit",
    "offset",
    "organizationId",
    "productId",
    "purl",
    "rating",
    "repositoryId",
    "resolved",
    "returnPurl",
    "rule",
    "severity",
    "sort",
    "status",
    "steps"
)

/**
 * The names of query parameters whose values are masked in the request log, because they can contain personal data or
 * arbitrary text. Values are masked for all parameters not listed in [LOGGED_QUERY_PARAMETERS] anyway, but listing
 * them explicitly allows to verify that every query parameter of the API was classified.
 */
internal val MASKED_QUERY_PARAMETERS = setOf(
    "filter",
    "licenseSearch",
    "search",
    "username"
)

/** The string that replaces the value of a masked query parameter. */
internal const val MASKED_VALUE = "***"

/**
 * Return the URI of this request for logging, where the values of all query parameters not contained in
 * [LOGGED_QUERY_PARAMETERS] are replaced with [MASKED_VALUE].
 */
internal fun ApplicationRequest.uriForLogging(): String = maskQueryString(path(), queryString())

/**
 * Return the given [path] with the given raw [queryString], where the values of all query parameters not contained in
 * [LOGGED_QUERY_PARAMETERS] are replaced with [MASKED_VALUE]. Logged parameters are kept in their original encoding.
 */
internal fun maskQueryString(path: String, queryString: String): String {
    if (queryString.isEmpty()) return path

    val maskedQuery = queryString.split('&').joinToString("&") { parameter ->
        val name = parameter.substringBefore('=')
        val decodedName = runCatching { name.decodeURLQueryComponent() }.getOrDefault(name)

        if ('=' !in parameter || decodedName in LOGGED_QUERY_PARAMETERS) parameter else "$name=$MASKED_VALUE"
    }

    return "$path?$maskedQuery"
}
