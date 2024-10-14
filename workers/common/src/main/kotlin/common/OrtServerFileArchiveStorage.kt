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

package org.eclipse.apoapsis.ortserver.workers.common

import java.io.InputStream

import org.eclipse.apoapsis.ortserver.storage.Key
import org.eclipse.apoapsis.ortserver.storage.Storage
import org.eclipse.apoapsis.ortserver.utils.logging.runBlocking

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.utils.ProvenanceFileStorage
import org.ossreviewtoolkit.scanner.Scanner

/**
 * An implementation of [ProvenanceFileStorage] which is used to store file archives as binary data using the provided
 * [storage]. This class is internally used by the [Scanner].
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

        /**
         * Generate the storage [Key] for the given [provenance].
         */
        private fun generateKey(provenance: KnownProvenance): Key =
            with(provenance) {
                when (this) {
                    is ArtifactProvenance -> Key("source-artifact|${sourceArtifact.url}|${sourceArtifact.hash}")
                    is RepositoryProvenance -> Key("vcs|${vcsInfo.type}|${vcsInfo.url}|$resolvedRevision")
                    else -> throw IllegalArgumentException("Unsupported provenance class ${this::class.simpleName}")
                }
            }
    }

    override fun getData(provenance: KnownProvenance): InputStream? = runBlocking {
        val key = generateKey(provenance)
        if (storage.containsKey(key)) storage.read(key).data else null
    }

    override fun hasData(provenance: KnownProvenance): Boolean = runBlocking {
        storage.containsKey(generateKey(provenance))
    }

    override fun putData(provenance: KnownProvenance, data: InputStream, size: Long) = runBlocking {
        storage.write(generateKey(provenance), data, size, CONTENT_TYPE)
    }
}
