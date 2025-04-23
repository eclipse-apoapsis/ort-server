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

package org.eclipse.apoapsis.ortserver.transport

/** The separator used for complex keys in message properties. */
private const val PROPERTY_SEPARATOR = "."

/**
 * Return a map that contains only the keys of this original [Map] that start with the provided [prefix], but with this
 * prefix removed. This function is intended to be used for the transport properties in a [MessageHeader]. The idea is
 * that the property keys start with the name of the transport they apply to. A concrete transport implementation can
 * use this function to obtain the relevant properties. Since the prefix gets stripped, it can then match on simple
 * property names. Per convention, the prefix is separated by a dot character ('.'); this character is added to the
 * passed in [prefix] if it is missing.
 */
fun Map<String, String>.selectByPrefix(prefix: String): Map<String, String> {
    val prefixWithSeparator = prefix.takeIf { it.endsWith(PROPERTY_SEPARATOR) } ?: "${prefix}$PROPERTY_SEPARATOR"

    return filter { it.key.startsWith(prefixWithSeparator) }.mapKeys { it.key.removePrefix(prefixWithSeparator) }
}
