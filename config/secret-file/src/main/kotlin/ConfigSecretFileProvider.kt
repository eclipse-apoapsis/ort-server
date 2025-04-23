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

package org.eclipse.apoapsis.ortserver.config.secret.file

import java.io.File

import org.eclipse.apoapsis.ortserver.config.ConfigSecretProvider
import org.eclipse.apoapsis.ortserver.config.Path

/**
 * An implementation of the [ConfigSecretProvider] interface that reads secrets from files.
 *
 * An instance is initialized with a collection of [File]s that contain the available secrets. Each file is expected to
 * be a text file that defines secrets in its lines. The syntax corresponds to property files; so each line should be of
 * the form `key=value`. Empty lines and lines starting with a '#' character are ignored.
 *
 * When the provider is queried for a secret, it reads the files in the provided order, line by line, until it finds
 * a key that matches the requested path. The value defined on this line is returned as secret value. So if there are
 * conflicting secret values, the first one that is encountered wins. This makes it possible to override selected
 * values in files.
 *
 * No caching is applied, so the files are read on each request for a secret. This is less efficient, but allows
 * picking up changes on secret values immediately.
 */
class ConfigSecretFileProvider(
    /**
     * The list of files with secrets to be read by this instance. The files are read in the provided order, so the
     * order is relevant.
     */
    private val secretFiles: Collection<File>
) : ConfigSecretProvider {
    companion object {
        /** The character separating a secret name from its value. */
        private const val KEY_VALUE_SEPARATOR = '='

        /** A character marking a line as comment line. */
        private const val COMMENT_CHARACTER = '#'

        /** A regular expression to split a line in the file into the secret name and its value. */
        private val splitKeyValueRegex = Regex("""\s*$KEY_VALUE_SEPARATOR\s*""")
    }

    override fun getSecret(path: Path): String {
        return getSecretValue(path.path, secretFiles.iterator())
    }

    private tailrec fun getSecretValue(name: String, filesIterator: Iterator<File>): String =
        if (filesIterator.hasNext()) {
            val matchingLine = filesIterator.next().bufferedReader().use { reader ->
                reader.lineSequence().map { it.trim() }
                    .filterNot { it.isEmpty() || it.startsWith(COMMENT_CHARACTER) || KEY_VALUE_SEPARATOR !in it }
                    .map { it.split(splitKeyValueRegex, limit = 2) }
                    .firstOrNull { it.first() == name }
            }

            if (matchingLine != null) {
                matchingLine.takeIf { it.size == 2 }?.get(1).orEmpty()
            } else {
                getSecretValue(name, filesIterator)
            }
        } else {
            throw NoSuchElementException("No value found for secret '$name'.")
        }
}
