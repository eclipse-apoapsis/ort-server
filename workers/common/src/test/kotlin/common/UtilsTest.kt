/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should

import io.mockk.every
import io.mockk.mockk

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.FileList as ModelFileList
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.scanner.FileList as ScannerFileList
import org.ossreviewtoolkit.scanner.utils.FileListResolver

class UtilsTest : WordSpec({
    "getFileLists" should {
        "return the expected file lists" {
            val provenance1 = RepositoryProvenance(VcsInfo(VcsType.GIT, "url", "revision"), "resolvedRevision")
            val provenance2 = ArtifactProvenance(RemoteArtifact("url", Hash.NONE))

            val fileList1 = ScannerFileList(
                ignorePatterns = setOf("ignorePattern1"),
                files = setOf(ScannerFileList.FileEntry("path1", "sha1"))
            )
            val fileList2 = ScannerFileList(
                ignorePatterns = setOf("ignorePattern2"),
                files = setOf(ScannerFileList.FileEntry("path2", "sha1"))
            )

            val fileListResolver = mockk<FileListResolver> {
                every { get(provenance1) } returns fileList1
                every { get(provenance2) } returns fileList2
            }

            val fileLists = getFileLists(fileListResolver, setOf(provenance1, provenance2))

            fileLists should containExactlyInAnyOrder(
                ModelFileList(
                    provenance1,
                    setOf(ModelFileList.Entry("path1", "sha1"))
                ),
                ModelFileList(
                    provenance2,
                    setOf(ModelFileList.Entry("path2", "sha1"))
                )
            )
        }

        "ignore provenances without stored file lists" {
            val provenance1 = RepositoryProvenance(VcsInfo(VcsType.GIT, "url", "revision"), "resolvedRevision")
            val provenance2 = ArtifactProvenance(RemoteArtifact("url", Hash.NONE))

            val fileListResolver = mockk<FileListResolver> {
                every { get(any<KnownProvenance>()) } returns null
            }

            val fileLists = getFileLists(fileListResolver, setOf(provenance1, provenance2))

            fileLists should beEmpty()
        }
    }
})
