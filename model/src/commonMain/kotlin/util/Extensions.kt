/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.model.util

/**
 * Remove the given [prefix] from this string if it is present, or return *null* otherwise.
 */
fun String.removePrefixOrNull(prefix: String): String? =
    takeIf { it.startsWith(prefix) }?.drop(prefix.length)

/**
 * Extract a numeric ID from this string after the given [prefix], or return *null* if the prefix is not present or the
 * ID is not a valid number.
 */
fun String.extractIdAfterPrefix(prefix: String): Long? =
    removePrefixOrNull(prefix)?.substringBefore('_')?.toLongOrNull()
