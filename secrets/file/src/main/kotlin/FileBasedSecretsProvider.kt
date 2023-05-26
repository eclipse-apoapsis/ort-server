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

package org.ossreviewtoolkit.server.secrets.file

import com.typesafe.config.Config

import java.io.File
import java.util.Base64

import kotlinx.serialization.json.Json

import org.ossreviewtoolkit.server.secrets.Path
import org.ossreviewtoolkit.server.secrets.Secret
import org.ossreviewtoolkit.server.secrets.SecretsProvider
import org.ossreviewtoolkit.server.secrets.file.model.FileBasedSecretsStorage
import org.ossreviewtoolkit.server.utils.config.getStringOrDefault

import org.slf4j.LoggerFactory

/**
 * A simple implementation of the [SecretsProvider] interface for local run purposes that uses a local file storage
 * to manage secrets.
 */
class FileBasedSecretsProvider(config: Config) : SecretsProvider {

    companion object {
        /** The name of this test provider implementation.*/
        const val NAME = "fileBased"

        /**
         * The path to the encrypted file storing the secrets
         */
        const val PATH_PROPERTY = "fileBasedPath"

        private val logger = LoggerFactory.getLogger(FileBasedSecretsProvider::class.java)
    }

    private val secretStorageFilePath = config.getStringOrDefault(PATH_PROPERTY, ".")

    /**
     * Return a map representing all secrets stored in file-based secret storage.
     */
    private fun readSecrets(): MutableMap<Path, Secret> {
        val file = getOrCreateStorageFile()

        val decodedSecrets = Base64.getDecoder().decode(file.readBytes())
        val serializer = FileBasedSecretsStorage.serializer()

        return Json.decodeFromString(
            serializer,
            String(decodedSecrets)
        ).secrets.map { (key, value) -> Path(key) to Secret(value) }.toMap().toMutableMap()
    }

    private fun getOrCreateStorageFile(): File {
        val file = File(secretStorageFilePath)

        if (!file.exists()) {
            logger.info(
                "The secrets storage file was not found in location `$secretStorageFilePath`. " +
                        "Creating an empty secrets storage file."
            )
            writeSecrets(mutableMapOf())
        }

        return file
    }

    /**
     * Return a map representing all secrets stored in file-based secret storage.
     */
    private fun writeSecrets(secrets: MutableMap<Path, Secret>) {
        val serializer = FileBasedSecretsStorage.serializer()
        val secretsJson = Json.encodeToString(
            serializer,
            FileBasedSecretsStorage(secrets.map { (key, value) -> key.path to value.value }.toMap().toMutableMap())
        )

        val encryptedSecrets = Base64.getEncoder().encode(secretsJson.toByteArray())

        File(secretStorageFilePath).writeBytes(encryptedSecrets)
    }

    @Synchronized
    override fun readSecret(path: Path): Secret? {
        return readSecrets()[path]
    }

    @Synchronized
    override fun writeSecret(path: Path, secret: Secret) {
        val secrets = readSecrets()
        secrets[path] = secret
        writeSecrets(secrets)
    }

    @Synchronized
    override fun removeSecret(path: Path) {
        val secrets = readSecrets()
        secrets -= path
        writeSecrets(secrets)
    }

    override fun createPath(
        organizationId: Long?,
        productId: Long?,
        repositoryId: Long?,
        secretName: String
    ): Path {
        val secretType = when {
            organizationId != null -> "organization"
            productId != null -> "product"
            repositoryId != null -> "repository"
            else -> throw IllegalArgumentException(
                "Either one of organizationId, productId or repositoryId should be specified to create a path."
            )
        }
        return Path(
            listOfNotNull(
                secretType,
                organizationId,
                productId,
                repositoryId,
                secretName
            ).joinToString("_")
        )
    }
}
