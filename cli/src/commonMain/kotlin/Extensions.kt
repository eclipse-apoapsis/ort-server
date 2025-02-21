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

package org.eclipse.apoapsis.ortserver.cli

import com.github.ajalt.clikt.parameters.options.NullableOption
import com.github.ajalt.clikt.parameters.options.transformAll

import org.eclipse.apoapsis.ortserver.cli.utils.getHomeDirectory

/**
 * Get the value of the environment variable with the given [name], or `null` if it is not set.
 */
expect fun getEnv(name: String): String?

/**
 * Add a trailing slash to this [String] if it is missing.
 */
fun String.ensureSuffix(suffix: String): String = takeIf { endsWith(suffix) } ?: "$this$suffix"

/**
 * Expand a leading tilde in this [String] to the user's home directory, if the caller is in a `SHELL` environment.
 * Otherwise, return the [String] as is.
 */
fun String.expandTilde() =
    if (getEnv("SHELL") != null) {
        replace("^~".toRegex(), Regex.escapeReplacement(getHomeDirectory().toString()))
    } else {
        this
    }

/**
 * If no option is given, fall back to the [fallback] value, return null if the [fallback] is null.
 */
fun <EachT : Any, ValueT> NullableOption<EachT, ValueT>.withFallback(fallback: EachT?): NullableOption<EachT, ValueT> =
    transformAll { it.lastOrNull() ?: fallback }
