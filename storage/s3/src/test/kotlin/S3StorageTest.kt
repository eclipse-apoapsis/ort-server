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

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.CreateBucketRequest
import aws.sdk.kotlin.services.s3.model.DeleteBucketRequest
import aws.sdk.kotlin.services.s3.model.DeleteObjectRequest
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.ListObjectsV2Request
import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.decodeToString
import aws.smithy.kotlin.runtime.net.url.Url

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.WordSpec
import io.kotest.extensions.testcontainers.TestContainerSpecExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.storage.Key
import org.eclipse.apoapsis.ortserver.storage.Storage
import org.eclipse.apoapsis.ortserver.storage.Storage.Companion.dataString
import org.eclipse.apoapsis.ortserver.storage.StorageException

import org.testcontainers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName

class S3StorageTest : WordSpec({
    val localStackContainer = install(
        TestContainerSpecExtension(
            LocalStackContainer(DockerImageName.parse(LOCALSTACK_IMAGE)).withServices("s3")
        )
    )

    val s3Client: S3Client by lazy {
        S3Client {
            region = localStackContainer.region
            this.endpointUrl = Url.parse(localStackContainer.endpoint.toString())
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = localStackContainer.accessKey
                secretAccessKey = localStackContainer.secretKey
            }
        }
    }

    beforeEach {
        s3Client.createBucket(CreateBucketRequest { bucket = TEST_BUCKET_NAME })
    }

    afterEach {
        s3Client.listObjectsV2(ListObjectsV2Request { bucket = TEST_BUCKET_NAME }).contents.orEmpty().forEach {
            s3Client.deleteObject(
                DeleteObjectRequest {
                    bucket = TEST_BUCKET_NAME
                    key = it.key
                }
            )
        }

        s3Client.deleteBucket(DeleteBucketRequest { bucket = TEST_BUCKET_NAME })
    }

    "write" should {
        "add an object into the S3 storage" {
            val key = Key("newObject")
            val data = "This is a test object for the S3 storage."
            val contentType = "application/octet-stream"

            val storage = localStackContainer.createStorage()

            storage.write(key, data, contentType)

            s3Client.getObject(
                GetObjectRequest {
                    bucket = TEST_BUCKET_NAME
                    this.key = key.key
                }
            ) { response ->
                response.body.shouldNotBeNull()
                response.body!!.decodeToString() shouldBe data
                response.contentType shouldBe contentType
            }
        }

        "override an object in the S3 storage" {
            val key = Key("object-key")
            val data = "The updated data of an S3 object."
            val contentType = "application/pdf"

            val storage = localStackContainer.createStorage()

            storage.write(key, "Old data of an object.", "text/plain")

            storage.write(key, data, contentType)

            s3Client.getObject(
                GetObjectRequest {
                    bucket = TEST_BUCKET_NAME
                    this.key = key.key
                }
            ) { response ->
                response.body.shouldNotBeNull()
                response.body!!.decodeToString() shouldBe data
                response.contentType shouldBe contentType
            }
        }
    }

    "read" should {
        "read an existing object from the s3 storage" {
            val key = Key("existingEntry")
            val data = "The data from the storage."
            val contentType = "application/octet-stream"

            s3Client.putObject {
                body = ByteStream.fromString(data)
                bucket = TEST_BUCKET_NAME
                this.contentType = contentType
                this.key = key.key
            }

            val storage = localStackContainer.createStorage()

            storage.read(key).use {
                it.contentType shouldBe contentType
                it.dataString shouldBe data
            }
        }

        "throw an exception if the key does not exist" {
            val storage = localStackContainer.createStorage()

            shouldThrow<StorageException> {
                storage.read(Key("object-not-existing"))
            }
        }
    }

    "contains" should {
        "return false for a non existing object" {
            val storage = localStackContainer.createStorage()

            storage.containsKey(Key("object-not-existing")) shouldBe false
        }

        "return true for an existing object" {
            val key = Key("test-object")

            val storage = localStackContainer.createStorage()

            storage.write(key, "test-data")

            storage.containsKey(key) shouldBe true
        }
    }

    "delete" should {
        "delete an object from the bucket" {
            val key = Key("test-object")

            val storage = localStackContainer.createStorage()

            storage.write(key, "test-data")

            storage.delete(key) shouldBe true
            storage.containsKey(key) shouldBe false
        }

        "return false for a non existing object" {
            val storage = localStackContainer.createStorage()

            storage.delete(Key("object-not-existing")) shouldBe false
        }
    }
})

internal const val LOCALSTACK_IMAGE = "localstack/localstack:3.4.0"
internal const val S3_REGION = "eu-central-1"
internal const val TEST_BUCKET_NAME = "test-bucket"

/**
 * Create a [Storage] that is configured to use the [S3StorageProvider] implementation.
 */
private fun LocalStackContainer.createStorage(region: String = S3_REGION, bucket: String = TEST_BUCKET_NAME): Storage {
    val config = ConfigFactory.parseMap(
        mapOf(
            bucket to mapOf(
                "name" to "s3",
                S3StorageProviderFactory.ENDPOINT_URL_PROPERTY to endpoint.toString(),
                S3StorageProviderFactory.ACCESS_KEY_PROPERTY to accessKey,
                S3StorageProviderFactory.SECRET_KEY_PROPERTY to secretKey,
                S3StorageProviderFactory.REGION_PROPERTY to region,
                S3StorageProviderFactory.BUCKET_NAME_PROPERTY to bucket
            )
        )
    )

    return Storage.create(bucket, ConfigManager.create(config))
}
