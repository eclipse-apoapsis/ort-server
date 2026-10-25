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

package org.eclipse.apoapsis.ortserver.storage

import kotlin.time.Instant

/**
 * A data class providing metadata about an entry in a storage without giving access to its data.
 *
 * Instances of this class are returned by the [StorageProvider.list] function.
 */
data class StorageEntryInfo(
    /** The key of the entry. */
    val key: Key,

    /** The length of the data of the entry in bytes. */
    val length: Long,

    /** The content type associated with the entry if available. */
    val contentType: String?,

    /** The time when the entry was last modified if available. */
    val lastModified: Instant?
)
