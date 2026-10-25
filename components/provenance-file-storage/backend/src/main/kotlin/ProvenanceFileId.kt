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

package org.eclipse.apoapsis.ortserver.components.provenancefilestorage

import kotlin.io.encoding.Base64

import org.eclipse.apoapsis.ortserver.storage.Key

/** The encoding used for IDs. As IDs are used in URLs, the URL-safe variant of Base64 without padding is used. */
private val idEncoding = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

/**
 * Return the ID of a [ProvenanceFile] stored under this [Key]. Storage keys contain URLs and separator characters;
 * therefore, they are encoded to obtain IDs that can be used as path segments.
 */
internal fun Key.toProvenanceFileId(): String = idEncoding.encode(key.encodeToByteArray())

/**
 * Decode the given [id] of a [ProvenanceFile] to the [Key] under which the file is stored. Return *null* if the [id] is
 * not valid.
 */
internal fun decodeProvenanceFileId(id: String): Key? =
    runCatching { Key(idEncoding.decode(id).decodeToString(throwOnInvalidSequence = true)) }.getOrNull()
