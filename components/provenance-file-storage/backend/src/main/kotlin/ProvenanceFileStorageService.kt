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

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList

import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderField
import org.eclipse.apoapsis.ortserver.storage.Key
import org.eclipse.apoapsis.ortserver.storage.Storage
import org.eclipse.apoapsis.ortserver.storage.StorageEntryInfo

/** An enumeration of the types of files stored per provenance. */
enum class ProvenanceFileType {
    /** File archives containing license and other relevant files of a provenance. */
    FILE_ARCHIVE,

    /** File lists containing the paths and checksums of all files of a provenance. */
    FILE_LIST
}

/**
 * A service for managing the files that are stored per provenance, i.e. file archives and file lists. As the
 * underlying storages only support listing all entries, the complete listing is loaded and filtering, sorting, and
 * paging are done in memory.
 */
class ProvenanceFileStorageService(
    /** The storage for file archives. */
    private val fileArchiveStorage: Storage,

    /** The storage for file lists. */
    private val fileListStorage: Storage
) {
    /**
     * Return the files of the given [type] whose URL, revision, hash, or key contains the given [filter] (ignoring
     * case), sorted and paged according to the given [parameters]. Supported sort fields are `url`, `sizeInBytes`,
     * and `lastModified`.
     */
    suspend fun list(
        type: ProvenanceFileType,
        filter: String?,
        parameters: ListQueryParameters
    ): ListQueryResult<ProvenanceFile> {
        val comparator = createComparator(parameters.sortFields)
        val files = loadFiles(type).filter { file -> filter.isNullOrBlank() || file.matches(filter) }

        val offset = parameters.offset ?: 0L
        val limit = parameters.limit ?: ListQueryParameters.DEFAULT_LIMIT
        val page = files.sortedWith(comparator).drop(offset.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).take(limit)

        return ListQueryResult(page, parameters.copy(limit = limit, offset = offset), files.size.toLong())
    }

    /**
     * Delete the file of the given [type] that is stored under the given [key]. Return *true* if the file existed and
     * was deleted, or *false* if it did not exist.
     */
    suspend fun delete(type: ProvenanceFileType, key: Key): Boolean = storageFor(type).delete(key)

    /** Load all files of the given [type] from the corresponding storage. */
    private suspend fun loadFiles(type: ProvenanceFileType): List<ProvenanceFile> =
        storageFor(type).list().map { it.toProvenanceFile() }.toList()

    /** Return the [Storage] for files of the given [type]. */
    private fun storageFor(type: ProvenanceFileType): Storage =
        when (type) {
            ProvenanceFileType.FILE_ARCHIVE -> fileArchiveStorage
            ProvenanceFileType.FILE_LIST -> fileListStorage
        }
}

/** Convert this [StorageEntryInfo] to a [ProvenanceFile] by parsing its key. */
internal fun StorageEntryInfo.toProvenanceFile(): ProvenanceFile {
    val file = ProvenanceFile(
        id = key.toProvenanceFileId(),
        key = key.key,
        kind = ProvenanceFileKind.UNKNOWN,
        url = null,
        vcsType = null,
        revision = null,
        hashValue = null,
        hashAlgorithm = null,
        sizeInBytes = length,
        lastModified = lastModified
    )

    return when (val parsedKey = parseStorageKey(key)) {
        is ParsedProvenanceKey.SourceArtifact -> file.copy(
            kind = ProvenanceFileKind.SOURCE_ARTIFACT,
            url = parsedKey.url,
            hashValue = parsedKey.hashValue,
            hashAlgorithm = parsedKey.hashAlgorithm
        )

        is ParsedProvenanceKey.Vcs -> file.copy(
            kind = ProvenanceFileKind.VCS,
            url = parsedKey.url,
            vcsType = parsedKey.vcsType,
            revision = parsedKey.revision
        )

        null -> file
    }
}

/** Check whether this [ProvenanceFile] matches the given [filter], ignoring case. */
private fun ProvenanceFile.matches(filter: String): Boolean =
    listOfNotNull(url, revision, hashValue, key).any { it.contains(filter, ignoreCase = true) }

/**
 * Create a [Comparator] for [ProvenanceFile]s based on the given [sortFields]. To get a stable order for paging, the
 * files are finally sorted by their keys.
 */
private fun createComparator(sortFields: List<OrderField>): Comparator<ProvenanceFile> {
    val fields = sortFields.ifEmpty { listOf(OrderField("url", OrderDirection.ASCENDING)) }

    return fields.map { field ->
        when (field.name) {
            "url" -> compareBy(nullsFirst(), ProvenanceFile::url)
            "sizeInBytes" -> compareBy(ProvenanceFile::sizeInBytes)
            "lastModified" -> compareBy(nullsFirst(), ProvenanceFile::lastModified)
            else -> throw QueryParametersException("Unsupported sort field: '${field.name}'.")
        }.withDirection(field.direction)
    }.reduce(Comparator<ProvenanceFile>::then).then(compareBy(ProvenanceFile::key))
}

private fun <T> Comparator<T>.withDirection(direction: OrderDirection): Comparator<T> =
    when (direction) {
        OrderDirection.ASCENDING -> this
        OrderDirection.DESCENDING -> reversed()
    }
