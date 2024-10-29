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
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.fromFile
import aws.smithy.kotlin.runtime.content.writeToFile

import java.io.InputStream

import kotlin.io.path.createTempFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.outputStream

import org.eclipse.apoapsis.ortserver.storage.Key
import org.eclipse.apoapsis.ortserver.storage.StorageEntry
import org.eclipse.apoapsis.ortserver.storage.StorageProvider
import org.eclipse.apoapsis.ortserver.utils.logging.runBlocking

/**
 * Implementation of the [StorageProvider] interface that is backed by AWS S3 storage.
 */
class S3StorageProvider(
    /** The S3 client to interact with AWS S3. */
    private val s3Client: S3Client,

    /** The name of the S3 bucket. */
    private val bucketName: String?
) : StorageProvider {
    /**
     * Retrieve the data associated with the given [key] from the S3 bucket.
     */
    override fun read(key: Key): StorageEntry = runBlocking {
        s3Client.getObject(
            GetObjectRequest {
                bucket = bucketName
                this.key = key.key
            }
        ) { response ->
            val data = checkNotNull(response.body) { "No data found for ${key.key}." }
            val contentType = response.contentType
            val tempFile = createTempFile()

            runCatching {
                data.writeToFile(tempFile)
                StorageEntry.create(tempFile.toFile(), contentType)
            }.onFailure { tempFile.deleteExisting() }.getOrThrow()
        }
    }

    /**
     * Write the given [data] to the S3 bucket with the provided [key].
     */
    override fun write(key: Key, data: InputStream, length: Long, contentType: String?): Unit = runBlocking {
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
                    this.key = key.key
                }
            )
        } finally {
            tempFile.deleteExisting()
        }
    }

    /**
     * Check if an object with the given [key] exists in the S3 bucket.
     */
    override fun contains(key: Key): Boolean = runBlocking {
        runCatching {
            s3Client.getObject(
                GetObjectRequest {
                    this.bucket = bucketName
                    this.key = key.key
                }
            ) {}
        }.isSuccess
    }

    /**
     * Delete the object for the provided key in the S3 bucket.
     */
    override fun delete(key: Key): Boolean = runBlocking {
        if (contains(key)) {
            runCatching {
                s3Client.deleteObject {
                    this.bucket = bucketName
                    this.key = key.key
                }
            }.isSuccess
        } else {
            false
        }
    }
}
