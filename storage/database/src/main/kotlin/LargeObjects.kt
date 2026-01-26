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

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.sql.Connection

import kotlin.io.path.outputStream

import org.eclipse.apoapsis.ortserver.storage.TempFileInputStream

import org.jetbrains.exposed.v1.jdbc.JdbcTransaction

import org.postgresql.PGConnection
import org.postgresql.largeobject.LargeObjectManager

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("LargeObjects")

/**
 * Use proprietary API of PostgreSQL to create a large object and populate it with the given [data]. Return the ID of
 * this object which needs to be stored in the storage table as a reference.
 */
internal fun JdbcTransaction.storeLargeObject(data: InputStream): Long {
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
internal fun JdbcTransaction.readLargeObject(oid: Long, size: Long, inMemoryLimit: Int): InputStream {
    val largeObjectManager = largeObjectManager(jdbcConnection())
    return getStreamForLargeObject(largeObjectManager, oid, size, inMemoryLimit)
}

/**
 * Delete the large object with the given [oid]. This is not done automatically when the referencing entity is deleted.
 */
internal fun JdbcTransaction.deleteLargeObject(oid: Long) {
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
): InputStream = largeObjectManager.open(oid, LargeObjectManager.READ).use { largeObject ->
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

/**
 * Return the JDBC [Connection] from this transaction. This is required for some low-level operations.
 */
private fun JdbcTransaction.jdbcConnection(): Connection =
    requireNotNull(connection.connection as? Connection) { "Cannot obtain JDBC connection." }

/**
 * Obtain the PostgreSQL API for managing large objects from the given [connection].
 */
private fun largeObjectManager(connection: Connection): LargeObjectManager =
    connection.unwrap(PGConnection::class.java).largeObjectAPI
