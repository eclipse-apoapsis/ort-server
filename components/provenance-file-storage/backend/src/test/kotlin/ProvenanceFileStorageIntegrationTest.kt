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

import io.ktor.client.HttpClient
import io.ktor.server.testing.ApplicationTestBuilder

import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractIntegrationTest
import org.eclipse.apoapsis.ortserver.storage.StorageEntryInfo

/** An [AbstractIntegrationTest] pre-configured for testing the provenance-file-storage routes. */
@Suppress("AbstractClassCanBeConcreteClass")
abstract class ProvenanceFileStorageIntegrationTest(
    body: ProvenanceFileStorageIntegrationTest.() -> Unit
) : AbstractIntegrationTest({}) {
    internal lateinit var fileArchiveProvider: FakeStorageProvider
    internal lateinit var fileListProvider: FakeStorageProvider
    lateinit var provenanceFileStorageService: ProvenanceFileStorageService

    init {
        beforeEach {
            val (fileArchiveStorage, archiveProvider) = createFakeStorage()
            val (fileListStorage, listProvider) = createFakeStorage()
            fileArchiveProvider = archiveProvider
            fileListProvider = listProvider
            provenanceFileStorageService = ProvenanceFileStorageService(fileArchiveStorage, fileListStorage)
        }

        body()
    }

    /** Add the given [entries] to the storage for files of the given [type]. */
    internal fun addEntries(type: ProvenanceFileType, vararg entries: StorageEntryInfo) {
        val provider = when (type) {
            ProvenanceFileType.FILE_ARCHIVE -> fileArchiveProvider
            ProvenanceFileType.FILE_LIST -> fileListProvider
        }

        entries.forEach { provider.entries[it.key] = it }
    }

    fun provenanceFileStorageTestApplication(
        block: suspend ApplicationTestBuilder.(client: HttpClient) -> Unit
    ) = integrationTestApplication(
        routes = { provenanceFileStorageRoutes(provenanceFileStorageService) },
        block = block
    )
}
