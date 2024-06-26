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

package org.eclipse.apoapsis.ortserver.config.github

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import io.ktor.utils.io.ByteReadChannel

class GitHubConfigNoCacheTest : WordSpec({
    "getOrPutFile" should {
        "return the data obtained from the load function" {
            val data = "test configuration data".toByteArray()
            val cache = GitHubConfigNoCache()

            val stream = cache.getOrPutFile("foo", "bar") {
                ByteReadChannel(data)
            }

            stream.readAllBytes() shouldBe data
        }
    }

    "getOrPutFolderContent" should {
        "return the data obtained from the load function" {
            val data = setOf("file1", "file2", "file3")
            val cache = GitHubConfigNoCache()

            val content = cache.getOrPutFolderContent("foo", "bar") {
                data
            }

            content shouldBe data
        }
    }
})
