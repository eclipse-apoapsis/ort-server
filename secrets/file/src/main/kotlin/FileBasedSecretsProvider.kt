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

package org.eclipse.apoapsis.ortserver.secrets.file

import com.typesafe.config.Config

import java.io.File

import kotlin.io.encoding.Base64

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

import org.eclipse.apoapsis.ortserver.secrets.Path
import org.eclipse.apoapsis.ortserver.secrets.SecretValue
import org.eclipse.apoapsis.ortserver.secrets.SecretsProvider
import org.eclipse.apoapsis.ortserver.secrets.file.model.FileBasedSecretsStorage
import org.eclipse.apoapsis.ortserver.shared.coroutines.Virtual
import org.eclipse.apoapsis.ortserver.utils.config.getStringOrDefault

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

    private val mutex = Mutex()

    private val secretStorageFilePath = config.getStringOrDefault(PATH_PROPERTY, ".")

    /**
     * Return a map representing all secrets stored in file-based secret storage.
     */
    private suspend fun readSecrets(): Map<Path, SecretValue> =
        withContext(Dispatchers.Virtual) {
            val file = getOrCreateStorageFile()
            val bytes = file.readBytes()
            if (bytes.isEmpty()) return@withContext emptyMap()

            val decodedSecrets = Base64.decode(bytes)

            return@withContext Json.decodeFromString<FileBasedSecretsStorage>(String(decodedSecrets)).secrets.entries
                .associate { (key, value) -> Path(key) to SecretValue(value) }
        }

    private suspend fun getOrCreateStorageFile(): File {
        val file = File(secretStorageFilePath)

        if (!file.isFile || file.length() == 0L) {
            logger.info(
                "No secrets storage content was found in file `$secretStorageFilePath`. " +
                        "Creating a file with empty secrets storage contents."
            )
            writeSecrets(emptyMap())
        }

        return file
    }

    /**
     * Return a map representing all secrets stored in file-based secret storage.
     */
    private suspend fun writeSecrets(secrets: Map<Path, SecretValue>) {
        withContext(Dispatchers.Virtual) {
            val secretsJson = Json.encodeToString<FileBasedSecretsStorage>(
                FileBasedSecretsStorage(secrets.entries.associate { (key, value) -> key.path to value.value })
            )

            val encryptedSecrets = Base64.encode(secretsJson.toByteArray())

            File(secretStorageFilePath).apply { parentFile.mkdirs() }.writeText(encryptedSecrets)
        }
    }

    override suspend fun readSecret(path: Path): SecretValue? = mutex.withLock { readSecrets()[path] }

    override suspend fun writeSecret(path: Path, secret: SecretValue) {
        mutex.withLock {
            val secrets = readSecrets().toMutableMap()
            secrets[path] = secret
            writeSecrets(secrets)
        }
    }

    override suspend fun removeSecret(path: Path) {
        mutex.withLock {
            val secrets = readSecrets().toMutableMap()
            secrets -= path
            writeSecrets(secrets)
        }
    }
}
