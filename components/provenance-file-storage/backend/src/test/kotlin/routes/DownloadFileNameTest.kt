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

package org.eclipse.apoapsis.ortserver.components.provenancefilestorage.routes

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ProvenanceFileType
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.artifactEntry
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.vcsEntry
import org.eclipse.apoapsis.ortserver.storage.Key

class DownloadFileNameTest : WordSpec({
    "downloadFileName" should {
        "use the repository name and revision for VCS provenances" {
            val key = vcsEntry("https://example.org/group/repo.git", "v1.0", 1).key

            downloadFileName(ProvenanceFileType.FILE_ARCHIVE, key) shouldBe "repo-v1.0.zip"
        }

        "use the artifact name and hash for source artifact provenances" {
            val key = artifactEntry("https://example.org/lib-1.0.tar.gz", "abc123", 1).key

            downloadFileName(ProvenanceFileType.FILE_LIST, key) shouldBe "lib-1.0.tar.gz-abc123.yml"
        }

        "ignore the query of the URL" {
            val key = artifactEntry("https://example.org/lib.tar.gz?mirror=/cache/download", "abc123", 1).key

            downloadFileName(ProvenanceFileType.FILE_LIST, key) shouldBe "lib.tar.gz-abc123.yml"
        }

        "ignore the fragment of the URL" {
            val key = vcsEntry("https://example.org/group/repo.git#path/to/dir", "v1.0", 1).key

            downloadFileName(ProvenanceFileType.FILE_ARCHIVE, key) shouldBe "repo-v1.0.zip"
        }

        "replace characters that are unsafe in file names" {
            val key = vcsEntry("https://example.org/repo/", "feature/my branch", 1).key

            downloadFileName(ProvenanceFileType.FILE_ARCHIVE, key) shouldBe "repo-feature_my_branch.zip"
        }

        "use the type for keys that cannot be parsed" {
            downloadFileName(ProvenanceFileType.FILE_ARCHIVE, Key("unknown")) shouldBe "file-archive.zip"
            downloadFileName(ProvenanceFileType.FILE_LIST, Key("unknown")) shouldBe "file-list.yml"
        }
    }
})
