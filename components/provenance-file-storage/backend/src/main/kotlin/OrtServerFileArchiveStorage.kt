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

package org.eclipse.apoapsis.ortserver.components.provenancefilestorage

import java.io.InputStream

import org.eclipse.apoapsis.ortserver.storage.Storage

import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.utils.ProvenanceFileStorage

/**
 * An implementation of [ProvenanceFileStorage] which is used to store file archives as binary data using the provided
 * [storage]. This class is internally used by the ORT scanner.
 */
class OrtServerFileArchiveStorage(
    /** The underlying [Storage] for persisting file archives. */
    private val storage: Storage
) : ProvenanceFileStorage {
    companion object {
        /** The storage type used for reports. */
        const val STORAGE_TYPE = "fileArchiveStorage"

        /** The content type used when writing data to the [storage]. */
        private const val CONTENT_TYPE = "application/octet-stream"
    }

    override suspend fun getData(provenance: KnownProvenance): InputStream? {
        val key = provenance.toStorageKey()
        return if (storage.containsKey(key)) storage.read(key).data else null
    }

    override suspend fun hasData(provenance: KnownProvenance): Boolean =
        storage.containsKey(provenance.toStorageKey())

    override suspend fun putData(provenance: KnownProvenance, data: InputStream, size: Long) {
        storage.write(provenance.toStorageKey(), data, size, CONTENT_TYPE)
    }
}
