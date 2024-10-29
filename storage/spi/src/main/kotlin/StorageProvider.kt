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

package org.eclipse.apoapsis.ortserver.storage

import java.io.InputStream

/**
 * Definition of an interface for accessing and managing data stored in a storage abstraction.
 *
 * This interface provides basic operations for reading, writing, and deleting (potentially) large data from a storage
 * implementation. It can be used for caching data temporarily, or for storing information for a longer time, such as
 * generated reports. Possible implementations could be external file stores typically available in cloud environments
 * or a dedicated database table with a BLOB column.
 *
 * To allow dealing with potentially big data with constant memory footprint, the API is based on streams.
 */
interface StorageProvider {
    /**
     * Return a [StorageEntry] that represents the data associated with the given [key]. Throw an exception if the
     * [key] does not exist.
     */
    fun read(key: Key): StorageEntry

    /**
     * Write the given [data] with the given [length] and optional [contentType] into this storage and associate it
     * with the given [key]. This function can be used for both creating and updating entries. The [length] may be
     * required explicitly by some implementations. Since it cannot be determined from the passed in [InputStream]
     * easily, it has to be provided by the caller. The [contentType] is optional; it may be useful when reading out
     * the data again, e.g. to download it via an HTTP request. The string provided here should thus follow the format
     * expected by a *Content-Type* header. This function does not close the [InputStream][data]; this needs to be
     * done by the caller. Throw an exception if the write operation fails.
     */
    fun write(key: Key, data: InputStream, length: Long, contentType: String? = null)

    /**
     * Return a flag whether an entry with the given [key] exists in this storage.
     */
    fun contains(key: Key): Boolean

    /**
     * Delete the entry with the given [key] from this storage. Return *true* if such an entry existed and was deleted;
     * return *false* if the entry did not exist. Throw an exception if the delete operation fails.
     */
    fun delete(key: Key): Boolean
}
