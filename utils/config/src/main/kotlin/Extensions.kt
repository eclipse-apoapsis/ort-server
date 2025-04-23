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

@file:Suppress("TooManyFunctions")

package org.eclipse.apoapsis.ortserver.utils.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory

/**
 * Return the boolean value with the given [path] or [default] if it cannot be found.
 */
fun Config.getBooleanOrDefault(path: String, default: Boolean): Boolean = getBooleanOrNull(path) ?: default

/**
 * Return the boolean value with the given [path] or `null` if it cannot be found.
 */
fun Config.getBooleanOrNull(path: String): Boolean? = withPath(path)?.getBoolean(path)

/**
 * Return the sub configuration at the given [path] or an empty [Config] if this path is not defined.
 */
fun Config.getConfigOrEmpty(path: String): Config =
    if (hasPath(path)) getConfig(path) else ConfigFactory.empty()

/**
 * Return the enum value with the [path] or [default] if it cannot be found. The enum is first looked up by name, but as
 * a fallback also by string representation.
 */
inline fun <reified E : Enum<E>> Config.getEnumOrDefault(path: String, default: E): E =
    runCatching {
        getEnum(E::class.java, path)
    }.getOrElse {
        when (it) {
            is ConfigException.Missing -> default

            is ConfigException.BadValue -> {
                val enumValue = getString(path)
                enumValues<E>().single { value -> value.toString() == enumValue }
            }

            else -> throw it
        }
    }

/**
 * Return the int value with the given [path] or [default] if it cannot be found.
 */
fun Config.getIntOrDefault(path: String, default: Int): Int = getIntOrNull(path) ?: default

/**
 * Return the int value with the given [path] or `null` if it cannot be found.
 */
fun Config.getIntOrNull(path: String): Int? = withPath(path)?.getInt(path)

/**
 * Return the long value with the given [path] or [default] if it cannot be found.
 */
fun Config.getLongOrDefault(path: String, default: Long): Long = getLongOrNull(path) ?: default

/**
 * Return the long value with the given [path] or `null` if it cannot be found.
 */
fun Config.getLongOrNull(path: String): Long? = withPath(path)?.getLong(path)

/**
 * Return the string value with the given [path] or [default] if it cannot be found.
 */
fun Config.getStringOrDefault(path: String, default: String): String = getStringOrNull(path) ?: default

/**
 * Return the string value with the given [path] or `null` if it cannot be found.
 */
fun Config.getStringOrNull(path: String): String? = withPath(path)?.getString(path)

/**
 * Return this [Config] if it contains the given [path] or null if not.
 */
fun Config.withPath(path: String): Config? = takeIf { hasPath(path) }

/** The prefix to indicate a variable. */
private const val VARIABLE_PREFIX = "\${"

/** The suffix of a variable. */
private const val VARIABLE_SUFFIX = "}"

/**
 * Return the string value at the given [path] applying variable interpolation using the given map of [variables].
 * The value in the configuration can contain placeholders using the typical `${variable}` syntax. This function
 * replaces such placeholders with the corresponding values in the map. If a variable cannot be resolved, the
 * placeholder remains unchanged.
 */
fun Config.getInterpolatedString(path: String, variables: Map<String, String>): String {
    val str = getString(path)

    return str.takeIf { VARIABLE_PREFIX !in it } ?: substituteVariables(str, variables)
}

/**
 * Return the string value at the given [path] applying variable interpolation using the given map of [variables] or
 * *null* if the path cannot be resolved. This is like [getInterpolatedString], but with optional configuration
 * properties.
 */
fun Config.getInterpolatedStringOrNull(path: String, variables: Map<String, String>): String? =
    withPath(path)?.getInterpolatedString(path, variables)

/**
 * Return the string value at the given [path] applying variable interpolation using the given map of [variables] or
 * [default] if the path cannot be resolved. [default] can contain variables as well that are replaced. This function
 * is like [getInterpolatedString], but with optional configuration properties for which defaults can be provided that
 * are also subject of variable substitution.
 */
fun Config.getInterpolatedStringOrDefault(path: String, default: String, variables: Map<String, String>): String =
    substituteVariables(getStringOrDefault(path, default), variables)

/**
 * Return a new string based on [string] with all variable references replaced by their current values in the given
 * [variables] map.
 */
private fun substituteVariables(string: String, variables: Map<String, String>): String =
    variables.filter { it.key in string }.entries.fold(string) { str, (key, value) ->
        val placeholder = "$VARIABLE_PREFIX${key}$VARIABLE_SUFFIX"
        str.replace(placeholder, value)
    }
