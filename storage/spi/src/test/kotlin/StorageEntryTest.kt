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

package org.eclipse.apoapsis.ortserver.storage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.paths.aFile
import io.kotest.matchers.paths.exist
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify

import java.io.InputStream

import kotlin.io.path.createTempFile

class StorageEntryTest : StringSpec({
    "create" should {
        "create an object with a file" {
            val tempFile = createTempFile()

            val entry = StorageEntry.create(tempFile.toFile(), "some-content")

            tempFile shouldBe aFile()

            entry.close()

            tempFile shouldNot exist()
        }

        "create an object with an input stream" {
            val stream = mockk<InputStream>()
            every { stream.close() } just runs

            val entry = StorageEntry.create(stream, "some-content", 0L)

            entry.close()

            verify {
                stream.close()
            }
        }
    }
})
