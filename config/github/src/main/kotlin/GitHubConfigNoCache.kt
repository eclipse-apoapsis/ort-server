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

package org.eclipse.apoapsis.ortserver.config.github

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.copyTo

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * An implementation of [GitHubConfigCache] that does not cache anything, but always delegates to the function that
 * fetches the requested data.
 */
internal class GitHubConfigNoCache : GitHubConfigCache {
    /**
     * This implementation ignores the passed in [revision] and [path] and always delegates to the [load] function.
     * Note that the whole content of the file is loaded into memory, which should be fine for configuration files.
     * (This is also in-line with the original implementation of [GitHubConfigFileProvider].)
     */
    override suspend fun getOrPutFile(
        revision: String,
        path: String,
        load: suspend () -> ByteReadChannel
    ): InputStream {
        val bos = ByteArrayOutputStream()
        load().copyTo(bos)

        return ByteArrayInputStream(bos.toByteArray())
    }
}
