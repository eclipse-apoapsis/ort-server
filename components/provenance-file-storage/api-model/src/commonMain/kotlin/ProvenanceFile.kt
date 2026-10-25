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

/** A file stored for a specific provenance, such as a file archive or a file list. */
@Serializable
data class ProvenanceFile(
    /** The ID of the file. This is an encoded form of the [key] that can be used in URLs. */
    val id: String,

    /** The key under which the file is stored in the storage. */
    val key: String,

    /** The kind of the provenance the file belongs to. */
    val kind: ProvenanceFileKind,

    /** The URL of the source artifact or the repository, or *null* if the [kind] is unknown. */
    val url: String?,

    /** The type of the VCS if the [kind] is [ProvenanceFileKind.VCS]. */
    val vcsType: String?,

    /** The resolved revision if the [kind] is [ProvenanceFileKind.VCS]. */
    val revision: String?,

    /** The value of the hash of the source artifact if the [kind] is [ProvenanceFileKind.SOURCE_ARTIFACT]. */
    val hashValue: String?,

    /** The algorithm of the hash of the source artifact if the [kind] is [ProvenanceFileKind.SOURCE_ARTIFACT]. */
    val hashAlgorithm: String?,

    /** The size of the file in bytes. */
    val sizeInBytes: Long,

    /** The time when the file was last modified, if provided by the storage. */
    val lastModified: Instant?
)

/** An enumeration of the kinds of provenances a [ProvenanceFile] can belong to. */
@Serializable
enum class ProvenanceFileKind {
    /** The file belongs to a source artifact. */
    SOURCE_ARTIFACT,

    /** The file belongs to a revision of a VCS repository. */
    VCS,

    /** The storage key of the file could not be parsed, so the kind of the provenance is unknown. */
    UNKNOWN
}
