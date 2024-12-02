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

import com.fasterxml.jackson.module.kotlin.readValue

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

import org.ossreviewtoolkit.model.FileList
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.model.yamlMapper

class OrtServerFileListStorageTest : WordSpec({
    lateinit var fileListStorage: OrtServerFileListStorage

    val provenance = RepositoryProvenance(
        vcsInfo = VcsInfo(
            type = VcsType.GIT,
            url = "https://example.org/repo.git",
            revision = "revision"
        ),
        resolvedRevision = "resolvedRevision"
    )

    val fileList = FileList(
        provenance = provenance,
        files = setOf(
            FileList.Entry("path1", "checksum1"),
            FileList.Entry("path2", "checksum2"),
            FileList.Entry("path3", "checksum3"),
            FileList.Entry("path4", "checksum4"),
            FileList.Entry("path5", "checksum5")
        )
    )

    beforeEach {
        fileListStorage = OrtServerFileListStorage(createStorage())
    }

    "getData" should {
        "return the file list if it exists" {
            fileListStorage.putFileList(provenance, fileList)

            val data = fileListStorage.getData(provenance)

            data.shouldNotBeNull()
            data.use { yamlMapper.readValue<FileList>(it) shouldBe fileList }
        }

        "return null if the file list does not exist" {
            fileListStorage.getData(provenance) should beNull()
        }
    }

    "hasData" should {
        "return true if the file list exists" {
            fileListStorage.putFileList(provenance, fileList)

            fileListStorage.hasData(provenance) shouldBe true
        }

        "return false if the file list does not exist" {
            fileListStorage.hasData(provenance) shouldBe false
        }
    }

    "putData" should {
        "store the provided data" {
            fileListStorage.putFileList(provenance, fileList)

            val data = fileListStorage.getData(provenance)

            data.shouldNotBeNull()
            data.use { yamlMapper.readValue<FileList>(it) shouldBe fileList }
        }

        "overwrite previously stored data for the same key" {
            fileListStorage.putFileList(provenance, FileList(provenance, emptySet()))
            fileListStorage.putFileList(provenance, fileList)

            val data = fileListStorage.getData(provenance)

            data.shouldNotBeNull()
            data.use { yamlMapper.readValue<FileList>(it) shouldBe fileList }
        }
    }
})

private fun OrtServerFileListStorage.putFileList(provenance: KnownProvenance, fileList: FileList) {
    val byteArray = fileList.toYaml().toByteArray()
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
