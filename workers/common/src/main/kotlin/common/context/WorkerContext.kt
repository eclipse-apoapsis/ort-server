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

package org.ossreviewtoolkit.server.workers.common.context

import java.io.File

import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.config.Path
import org.ossreviewtoolkit.server.model.Hierarchy
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.Secret

/**
 * An interface providing information and services useful to multiple worker implementations.
 *
 * Workers requiring this functionality can obtain an instance from a [WorkerContextFactory].
 *
 * Some functionality of this interface can consume resources that should be released when they are no longer needed.
 * This can be done via the [close] function.
 */
interface WorkerContext : AutoCloseable {
    /** The [OrtRun] that is to be processed. */
    val ortRun: OrtRun

    /** An object with information about the current repository and its hierarchy. */
    val hierarchy: Hierarchy

    /** The object providing access to the ORT Server configuration. */
    val configManager: ConfigManager

    /**
     * Resolve the given [secret] and return its value. Cache the value, so that it can be returned directly when a
     * [Secret] with the same path is queried again.
     */
    suspend fun resolveSecret(secret: Secret): String

    /**
     * Resolve the given [secrets] in parallel and return a map with their values. Also add them to the internal cache,
     * so that they are directly available when their values are queried via [resolveSecret]. For clients having to
     * deal with multiple secrets, using this function is more efficient than multiple calls of [resolveSecret] in a
     * sequence.
     */
    suspend fun resolveSecrets(vararg secrets: Secret): Map<Secret, String>

    /**
     * Download the configuration file at the specified [path] from the resolved configuration context to a temporary
     * directory. The downloaded file is registered internally; it is removed automatically when this context is
     * closed.
     */
    suspend fun downloadConfigurationFile(path: Path): File

    /**
     * Download all the configuration files in the given [paths] collection from the resolved configuration context to
     * a temporary directory. Return a [Map] that allows access to the temporary files by their paths. The downloaded
     * files are registered internally; they are removed automatically when this context is closed.
     */
    suspend fun downloadConfigurationFiles(paths: Collection<Path>): Map<Path, File>
}
