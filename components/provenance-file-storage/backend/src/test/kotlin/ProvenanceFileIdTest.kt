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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

import org.eclipse.apoapsis.ortserver.storage.Key

class ProvenanceFileIdTest : WordSpec({
    "toProvenanceFileId" should {
        "produce IDs that can be used in URLs" {
            val id = Key("vcs|Git|https://example.org/repo.git?a=b&c=d#fragment|revision").toProvenanceFileId()

            listOf("/", "+", "=", "|", "?", "&", "#").forEach { id shouldNotContain it }
        }
    }

    "decodeProvenanceFileId" should {
        "restore the original key" {
            val key = Key("source-artifact|https://example.org/ärtifact.zip|Hash(value=abc, algorithm=SHA-1)")

            decodeProvenanceFileId(key.toProvenanceFileId()) shouldBe key
        }

        "return null for invalid IDs" {
            decodeProvenanceFileId("not base64!") should beNull()
        }

        "return null for IDs that do not decode to valid UTF-8" {
            decodeProvenanceFileId("_w") should beNull()
        }
    }
})
