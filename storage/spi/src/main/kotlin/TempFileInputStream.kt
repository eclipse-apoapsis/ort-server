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

package org.eclipse.apoapsis.ortserver.storage

import java.io.File
import java.io.InputStream

/**
 * A specialized [InputStream] implementation that operates on a temporary file. The read functions are implemented
 * to access the input stream from this file. When the stream is closed, the file is deleted.
 *
 * This class is used to work around the limitation that PostgreSQL large objects can only be accessed during a
 * transaction. Most use cases, however, require reading the stream after the transaction. Therefore, to avoid that the
 * whole data needs to be read in memory, the storage provider implementation creates a temporary file first and then
 * exposes the stream from this file.
 *
 * Note: This would be a good use case for delegation, but unfortunately, this only works for interfaces.
 */
class TempFileInputStream(
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
