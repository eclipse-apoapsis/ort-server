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

package org.eclipse.apoapsis.ortserver.shared.ktorutils

import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.MissingRequestParameterException
import io.ktor.server.plugins.ParameterConversionException

import org.eclipse.apoapsis.ortserver.shared.apimodel.FilterOptions

/**
 * Return the numeric value of the parameter with the given [name] or throw an exception if it cannot be converted to a
 * number.
 */
fun ApplicationCall.numberParameter(name: String): Number? =
    try {
        parameters[name]?.toLong()
    } catch (e: NumberFormatException) {
        throw ParameterConversionException(name, "Number", e)
    }

/**
 * Get the parameter from this [ApplicationCall] or throw an exception if the parameter is null.
 */
fun ApplicationCall.requireParameter(name: String) = parameters[name] ?: throw MissingRequestParameterException(name)

/**
 * Get the ID parameter from this [ApplicationCall] or throw an exception if the parameter is null or not a valid ID.
 */
fun ApplicationCall.requireIdParameter(name: String): Long {
    val id = requireParameter(name).toLongOrNull()

    return if (id != null && id > 0) id else throw ParameterConversionException(name, "ID")
}

/**
 * Get the filter parameter from this [ApplicationCall] or return null if the parameter is null.
 */
fun ApplicationCall.filterParameter(name: String): FilterOptions? {
    val filter = parameters[name]?.let { filter ->
        FilterOptions(filter)
    }
    return filter
}

inline fun <reified T : Enum<T>> ApplicationCall.requireEnumParameter(name: String): T =
    runCatching { enumValueOf<T>(requireParameter(name)) }
        .getOrElse { throw ParameterConversionException(name, "Enum<${T::class.simpleName}>") }
