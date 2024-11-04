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

package org.eclipse.apoapsis.ortserver.storage.database

import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.file.exist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.types.shouldBeInstanceOf

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify

import java.io.ByteArrayInputStream
import java.io.File

import org.eclipse.apoapsis.ortserver.storage.TempFileInputStream

import org.postgresql.largeobject.LargeObject
import org.postgresql.largeobject.LargeObjectManager

class LargeObjectsTest : WordSpec({
    "TempFileInputStream" should {
        "read data byte-wise" {
            val data = "The data to be read."
            val file = createTempFile(data.toByteArray())

            TempFileInputStream(file).use { stream ->
                stream.read() shouldBe 'T'.code.toByte()
                stream.read() shouldBe 'h'.code.toByte()
            }
        }

        "read an array of data" {
            val bufSize = 8
            val data = "This is a block of data to be read"
            val file = createTempFile(data.toByteArray())

            TempFileInputStream(file).use { stream ->
                val buf = ByteArray(bufSize)
                stream.read(buf) shouldBe bufSize
                buf shouldBe data.substring(0, bufSize).toByteArray()
            }
        }

        "read a part of a byte array" {
            val data = ByteArray(16) { idx -> idx.toByte() }
            val file = createTempFile(data)

            TempFileInputStream(file).use { stream ->
                val buf = ByteArray(16)
                stream.read(buf, 4, 8) shouldBe 8
                buf shouldBe byteArrayOf(0, 0, 0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 0, 0, 0, 0)
            }
        }

        "delete the temporary file when it is closed" {
            val file = createTempFile(byteArrayOf(1, 2, 3))

            val stream = TempFileInputStream(file)
            stream.close()

            file shouldNot exist()
        }
    }

    "getStreamForLargeObject" should {
        "read the data into memory if it fits" {
            val oid = 42L
            val data = "The data of the large object.".toByteArray()

            val lom = mockk<LargeObjectManager>()
            val obj = mockk<LargeObject>()
            every { lom.open(oid, LargeObjectManager.READ) } returns obj
            every { obj.inputStream } returns ByteArrayInputStream(data)
            every { obj.close() } just runs

            val stream = getStreamForLargeObject(lom, oid, data.size.toLong(), data.size)

            stream.shouldBeInstanceOf<ByteArrayInputStream>()
            stream.use { it.readAllBytes() } shouldBe data

            verify { obj.close() }
        }

        "create a temporary file if necessary" {
            val oid = 43L
            val data = "This is really huge data that does not fit into memory.".toByteArray()

            val lom = mockk<LargeObjectManager>()
            val obj = mockk<LargeObject>()
            every { lom.open(oid, LargeObjectManager.READ) } returns obj
            every { obj.inputStream } returns ByteArrayInputStream(data)
            every { obj.close() } just runs

            getStreamForLargeObject(lom, oid, data.size.toLong(), data.size - 1).use { stream ->
                stream.shouldBeInstanceOf<TempFileInputStream>()
                stream.use { it.readAllBytes() } shouldBe data
            }

            verify { obj.close() }
        }
    }
})

/**
 * Create a file in a temporary directory and populate it with the given [data].
 */
private fun Spec.createTempFile(data: ByteArray): File {
    val dir = tempdir()
    val file = dir.resolve("test.tmp")

    file.writeBytes(data)
    return file
}
