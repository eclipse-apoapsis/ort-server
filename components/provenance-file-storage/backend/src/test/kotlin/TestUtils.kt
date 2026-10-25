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

import java.io.InputStream

import kotlin.time.Clock
import kotlin.time.Instant

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

import org.eclipse.apoapsis.ortserver.storage.Key
import org.eclipse.apoapsis.ortserver.storage.Storage
import org.eclipse.apoapsis.ortserver.storage.StorageEntry
import org.eclipse.apoapsis.ortserver.storage.StorageEntryInfo
import org.eclipse.apoapsis.ortserver.storage.StorageProvider

/**
 * A simple [StorageProvider] implementation for tests that only manages the metadata of entries. It records how often
 * the entries have been listed.
 */
internal class FakeStorageProvider(entries: Collection<StorageEntryInfo> = emptyList()) : StorageProvider {
    /** The entries of this storage, mapped by their keys. */
    val entries = entries.associateByTo(mutableMapOf()) { it.key }

    /** The number of times the entries of this storage have been listed. */
    var listCount = 0
        private set

    override suspend fun read(key: Key): StorageEntry = throw UnsupportedOperationException()

    override suspend fun write(key: Key, data: InputStream, length: Long, contentType: String?) {
        entries[key] = StorageEntryInfo(key, length, contentType, lastModified = null)
    }

    override suspend fun contains(key: Key): Boolean = key in entries

    override suspend fun delete(key: Key): Boolean = entries.remove(key) != null

    override fun list(prefix: String): Flow<StorageEntryInfo> {
        listCount++
        return entries.values.filter { it.key.key.startsWith(prefix) }.asFlow()
    }
}

/** Create a [Storage] backed by a [FakeStorageProvider] with the given [entries]. */
internal fun createFakeStorage(vararg entries: StorageEntryInfo): Pair<Storage, FakeStorageProvider> {
    val provider = FakeStorageProvider(entries.toList())
    return Storage(provider) to provider
}

/** Create a [StorageEntryInfo] for a VCS provenance with the given properties. */
internal fun vcsEntry(url: String, revision: String, size: Long, lastModified: Instant? = null) =
    StorageEntryInfo(Key("vcs|Git|$url|$revision"), size, contentType = null, lastModified = lastModified)

/** Create a [StorageEntryInfo] for a source artifact provenance with the given properties. */
internal fun artifactEntry(url: String, hash: String, size: Long, lastModified: Instant? = null) =
    StorageEntryInfo(
        Key("source-artifact|$url|Hash(value=$hash, algorithm=SHA-1)"),
        size,
        contentType = null,
        lastModified = lastModified
    )

/** A [Clock] that always returns the given [now] time. */
internal class FixedClock(var now: Instant) : Clock {
    override fun now(): Instant = now
}
