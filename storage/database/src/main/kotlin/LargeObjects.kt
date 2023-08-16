/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.storage.database

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.sql.Connection

import kotlin.io.path.outputStream

import org.jetbrains.exposed.sql.Transaction

import org.postgresql.PGConnection
import org.postgresql.largeobject.LargeObjectManager

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("LargeObjects")

/**
 * Use proprietary API of PostgreSQL to create a large object and populate it with the given [data]. Return the ID of
 * this object which needs to be stored in the storage table as a reference.
 */
internal fun Transaction.storeLargeObject(data: InputStream): Long {
    val largeObjectManager = largeObjectManager(jdbcConnection())

    val oid = largeObjectManager.createLO(LargeObjectManager.READWRITE)
    largeObjectManager.open(oid, LargeObjectManager.WRITE).use { obj ->
        data.copyTo(obj.outputStream)
    }

    return oid
}

/**
 * Return an [InputStream] to read the data of the large object with the given [oid]. Check the given [size] against
 * the configured [inMemoryLimit].
 */
internal fun Transaction.readLargeObject(oid: Long, size: Long, inMemoryLimit: Int): InputStream {
    val largeObjectManager = largeObjectManager(jdbcConnection())
    return getStreamForLargeObject(largeObjectManager, oid, size, inMemoryLimit)
}

/**
 * Delete the large object with the given [oid]. This is not done automatically when the referencing entity is deleted.
 */
internal fun Transaction.deleteLargeObject(oid: Long) {
    val largeObjectManager = largeObjectManager(jdbcConnection())
    largeObjectManager.delete(oid)
}

/**
 * Return an [InputStream] to read the data of the large object with the given [oid] using the given
 * [largeObjectManager]. If the object fits into memory (according to the given [object size][size] and
 * [limit][inMemoryLimit]), return a [ByteArrayInputStream]. Otherwise, copy its data into a temporary file and
 * return a stream from this file.
 */
internal fun getStreamForLargeObject(
    largeObjectManager: LargeObjectManager,
    oid: Long,
    size: Long,
    inMemoryLimit: Int
): InputStream {
    return largeObjectManager.open(oid, LargeObjectManager.READ).use { largeObject ->
        if (size <= inMemoryLimit) {
            logger.debug("Loading data of size {} into memory.", size)

            val buffer = ByteArrayOutputStream(size.toInt())
            largeObject.inputStream.copyTo(buffer)
            ByteArrayInputStream(buffer.toByteArray())
        } else {
            val tempFile = Files.createTempFile("dbstorage", "tmp")
            logger.debug("Storing data of size {} in temporary file '{}'.", size, tempFile)

            largeObject.inputStream.copyTo(tempFile.outputStream())
            TempFileInputStream(tempFile.toFile())
        }
    }
}

/**
 * A specialized [InputStream] implementation that operates on a temporary file. The read functions are implemented
 * to access the input stream from this file. When the stream is closed the file is deleted.
 *
 * This class is used to work around the limitation that PostgreSQL large objects can only be accessed during a
 * transaction. Most use cases, however, require reading the stream after the transaction. Therefore, to avoid that the
 * whole data needs to be read in memory, the storage provider implementation creates a temporary file first and then
 * exposes the stream from this file.
 *
 * Note: This would be good use case for delegation, but unfortunately, this only works for interfaces.
 */
internal class TempFileInputStream(
    /** The temporary file to wrap. */
    private val tempFile: File
) : InputStream() {
    /** The stream to the temporary file to which all read operations are delegated. */
    private val tempStream = tempFile.inputStream()

    override fun close() {
        tempStream.close()
        tempFile.delete()
    }

    override fun read(): Int = tempStream.read()

    override fun read(b: ByteArray): Int = tempStream.read(b)

    override fun read(b: ByteArray, off: Int, len: Int): Int = tempStream.read(b, off, len)
}

/**
 * Return the JDBC [Connection] from this transaction. This is required for some low-level operations.
 */
private fun Transaction.jdbcConnection(): Connection =
    requireNotNull(connection.connection as? Connection) { "Cannot obtain JDBC connection." }

/**
 * Obtain the PostgreSQL API for managing large objects from the given [connection].
 */
private fun largeObjectManager(connection: Connection): LargeObjectManager =
    connection.unwrap(PGConnection::class.java).largeObjectAPI
