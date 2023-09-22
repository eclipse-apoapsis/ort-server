/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.utils.config

import com.typesafe.config.Config
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
