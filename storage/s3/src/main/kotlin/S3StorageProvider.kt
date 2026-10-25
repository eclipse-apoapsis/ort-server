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

package org.eclipse.apoapsis.ortserver.storage.s3

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.deleteObject
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.HeadObjectRequest
import aws.sdk.kotlin.services.s3.model.ListObjectsV2Request
import aws.sdk.kotlin.services.s3.model.Object
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.paginators.listObjectsV2Paginated
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.fromFile
import aws.smithy.kotlin.runtime.content.writeToFile

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

import kotlin.io.encoding.Base64
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.outputStream
import kotlin.time.Instant

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

import org.eclipse.apoapsis.ortserver.storage.Key
import org.eclipse.apoapsis.ortserver.storage.StorageEntry
import org.eclipse.apoapsis.ortserver.storage.StorageEntryInfo
import org.eclipse.apoapsis.ortserver.storage.StorageProvider

import org.ossreviewtoolkit.utils.common.calculateHash

/** Implementation of the [StorageProvider] interface that is backed by AWS S3 storage. */
class S3StorageProvider(
    /** The S3 client to interact with AWS S3. */
    private val s3Client: S3Client,

    /** The name of the S3 bucket. */
    private val bucketName: String?,

    /** An optional prefix for the storage keys. */
    private val keyPrefix: String?
) : StorageProvider {
    /** Retrieve the data associated with the given [key] from the S3 bucket. */
    override suspend fun read(key: Key): StorageEntry = s3Client.getObject(
        GetObjectRequest {
            bucket = bucketName
            this.key = prefixKey(key)
        }
    ) { response ->
        val data = checkNotNull(response.body) { "No data found for ${prefixKey(key)}." }
        val contentType = response.contentType
        val tempFile = createTempFile()

        runCatching {
            data.writeToFile(tempFile)
            StorageEntry.create(tempFile.toFile(), contentType)
        }.onFailure { tempFile.deleteExisting() }.getOrThrow()
    }

    /** Write the given [data] to the S3 bucket with the provided [key]. */
    override suspend fun write(key: Key, data: InputStream, length: Long, contentType: String?) {
        val tempFile = createTempFile()

        try {
            tempFile.outputStream().use { outputStream ->
                data.copyTo(outputStream)
            }

            s3Client.putObject(
                PutObjectRequest {
                    bucket = bucketName
                    body = ByteStream.fromFile(tempFile.toFile())
                    this.contentType = contentType
                    this.key = prefixKey(key)
                    checksumSha256 = calculateSha256Checksum(tempFile.toFile())
                }
            )
        } finally {
            tempFile.deleteExisting()
        }
    }

    /** Check if an object with the given [key] exists in the S3 bucket. */
    override suspend fun contains(key: Key): Boolean = runCatching {
        s3Client.headObject(
            HeadObjectRequest {
                this.bucket = bucketName
                this.key = prefixKey(key)
            }
        )
    }.isSuccess

    /** Delete the object for the provided key in the S3 bucket. */
    override suspend fun delete(key: Key): Boolean = if (contains(key)) {
        runCatching {
            s3Client.deleteObject {
                this.bucket = bucketName
                this.key = prefixKey(key)
            }
        }.isSuccess
    } else {
        false
    }

    /**
     * List the objects in the S3 bucket whose keys start with the given [prefix]. If a key prefix is configured, it is
     * removed from the returned keys, and objects without this prefix are ignored.
     */
    override fun list(prefix: String): Flow<StorageEntryInfo> = flow {
        val request = ListObjectsV2Request {
            bucket = bucketName
            this.prefix = prefixKey(Key(prefix))
        }

        s3Client.listObjectsV2Paginated(request).collect { response ->
            response.contents.orEmpty().forEach { obj ->
                obj.toStorageEntryInfo()?.let { emit(it) }
            }
        }
    }

    private fun prefixKey(key: Key) = when {
        keyPrefix != null -> "$keyPrefix|${key.key}"
        else -> key.key
    }

    /**
     * Convert this [Object] to a [StorageEntryInfo]. Return *null* if the key of the object does not have the
     * configured key prefix.
     */
    private fun Object.toStorageEntryInfo(): StorageEntryInfo? =
        key?.let(::unprefixKey)?.let { unprefixedKey ->
            StorageEntryInfo(
                key = Key(unprefixedKey),
                length = size ?: 0L,
                contentType = null,
                lastModified = lastModified?.let { Instant.fromEpochSeconds(it.epochSeconds, it.nanosecondsOfSecond) }
            )
        }

    /** Remove the configured key prefix from the given [key]. Return *null* if the key does not have this prefix. */
    private fun unprefixKey(key: String): String? = when {
        keyPrefix != null -> key.removePrefix("$keyPrefix|").takeIf { it.length < key.length }
        else -> key
    }
}

/** The algorithm used for checksum calculation. */
private const val CHECKSUM_ALGORITHM = "SHA-256"

/** Calculate a SHA256 checksum for the given [data] file. This is used to verify the integrity of uploaded data. */
private fun calculateSha256Checksum(data: File): String =
    Base64.encode(calculateHash(data, MessageDigest.getInstance(CHECKSUM_ALGORITHM)))
