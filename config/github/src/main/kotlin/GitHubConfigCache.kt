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

import java.io.InputStream

/**
 * Definition of an interface for a cache to store GitHub configuration data.
 *
 * During an ORT run, [GitHubConfigFileProvider] typically loads many configuration files from the configured GitHub
 * repository. This can be problematic for multiple reasons:
 * - The GitHub API has a rate limit of 5000 requests per hour, which can be exceeded quickly.
 * - There is a strong dependency to the availability of the GitHub API.
 * - The same data is requested multiple times during an ORT run, which is inefficient.
 *
 * To address these issues, this caching interface is introduced. Currently, it is not intended to support custom
 * implementations, but there will be a default implementation using a file-based cache and a no-op implementation for
 * turning off caching.
 */
interface GitHubConfigCache {
    /**
     * Return an [InputStream] to access the file at the given [path] from the given [revision]. Use the given [load]
     * function to obtain the file content if necessary. The [load] function returns a channel with the content of the
     * file to be cached; this is typically obtained via an HTTP request to the GitHub API. A concrete cache
     * implementation persists this data and returns an [InputStream] to access it (which is also the return type
     * for files requested from a config file provider).
     */
    suspend fun getOrPutFile(revision: String, path: String, load: suspend () -> ByteReadChannel): InputStream
}
