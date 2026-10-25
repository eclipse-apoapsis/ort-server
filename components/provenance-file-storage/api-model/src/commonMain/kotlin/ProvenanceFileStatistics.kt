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

import kotlin.time.Instant

import kotlinx.serialization.Serializable

/** Statistics about all stored [ProvenanceFile]s of a specific type. */
@Serializable
data class ProvenanceFileStatistics(
    /** The time when the files were listed from the storage. The statistics reflect the state at this time. */
    val snapshotTime: Instant,

    /** The total number of files. */
    val totalCount: Long,

    /** The total size of all files in bytes. */
    val totalSizeInBytes: Long,

    /** The average size of the files in bytes, or 0 if there are no files. */
    val averageSizeInBytes: Long,

    /** The largest file, or *null* if there are no files. */
    val largest: ProvenanceFile?,

    /** The oldest modification time of a file, or *null* if not available. */
    val oldestLastModified: Instant?,

    /** The newest modification time of a file, or *null* if not available. */
    val newestLastModified: Instant?,

    /** Statistics for each kind of provenance that occurs. */
    val byKind: List<ProvenanceFileKindStatistics>,

    /** The URLs with the largest total size of files, in descending order of the size. */
    val topUrls: List<ProvenanceFileUrlStatistics>
)

/** Statistics about the stored [ProvenanceFile]s of a specific [kind]. */
@Serializable
data class ProvenanceFileKindStatistics(
    /** The kind of provenance. */
    val kind: ProvenanceFileKind,

    /** The number of files of this kind. */
    val count: Long,

    /** The total size of the files of this kind in bytes. */
    val sizeInBytes: Long
)

/** Statistics about the stored [ProvenanceFile]s for a specific [url] of a source artifact or a repository. */
@Serializable
data class ProvenanceFileUrlStatistics(
    /** The URL of the source artifact or the repository. */
    val url: String,

    /** The number of files for this URL, e.g. one per revision of a repository. */
    val count: Long,

    /** The total size of the files for this URL in bytes. */
    val sizeInBytes: Long
)
