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

import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.ByteArrayInputStream

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.storage.Storage
import org.eclipse.apoapsis.ortserver.storage.StorageProviderFactoryForTesting

import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

class OrtServerFileArchiveStorageTest : WordSpec({
    lateinit var fileArchiveStorage: OrtServerFileArchiveStorage

    val provenance = RepositoryProvenance(
        vcsInfo = VcsInfo(
            type = VcsType.GIT,
            url = "https://example.org/repo.git",
            revision = "revision"
        ),
        resolvedRevision = "resolvedRevision"
    )

    val archiveContent = "The archive file content."

    beforeEach {
        fileArchiveStorage = OrtServerFileArchiveStorage(createStorage())
    }

    "getData" should {
        "return the file archive if it exists" {
            fileArchiveStorage.putArchive(provenance, archiveContent)

            val data = fileArchiveStorage.getData(provenance)

            data.shouldNotBeNull()
            data.use { it.bufferedReader().readText() shouldBe archiveContent }
        }

        "return null if the file list does not exist" {
            fileArchiveStorage.getData(provenance) should beNull()
        }
    }

    "hasData" should {
        "return true if the file list exists" {
            fileArchiveStorage.putArchive(provenance, archiveContent)

            fileArchiveStorage.hasData(provenance) shouldBe true
        }

        "return false if the file list does not exist" {
            fileArchiveStorage.hasData(provenance) shouldBe false
        }
    }

    "putData" should {
        "store the provided data" {
            fileArchiveStorage.putArchive(provenance, archiveContent)

            val data = fileArchiveStorage.getData(provenance)

            data.shouldNotBeNull()
            data.use { it.bufferedReader().readText() shouldBe archiveContent }
        }

        "overwrite previously stored data for the same key" {
            fileArchiveStorage.putArchive(provenance, "Previous content.")
            fileArchiveStorage.putArchive(provenance, archiveContent)

            val data = fileArchiveStorage.getData(provenance)

            data.shouldNotBeNull()
            data.use { it.bufferedReader().readText() shouldBe archiveContent }
        }
    }
})

private fun OrtServerFileArchiveStorage.putArchive(provenance: KnownProvenance, content: String) {
    val byteArray = content.toByteArray()
    putData(provenance, ByteArrayInputStream(byteArray), byteArray.size.toLong())
}

/**
 * Create the [Storage] to be used for tests. This is a test storage, so the stored data can be inspected.
 */
private fun createStorage(): Storage {
    val storageType = "test"
    val configMap = mapOf(storageType to mapOf("name" to StorageProviderFactoryForTesting.NAME))
    val config = ConfigFactory.parseMap(configMap)

    return Storage.create(storageType, ConfigManager.create(config))
}
