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

import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.model.Hierarchy
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.Secret

/**
 * An interface providing information and services useful to multiple worker implementations.
 *
 * Workers requiring this functionality can obtain an instance from a [WorkerContextFactory].
 */
interface WorkerContext {
    /** The [OrtRun] that is to be processed. */
    val ortRun: OrtRun

    /** An object with information about the current repository and its hierarchy. */
    val hierarchy: Hierarchy

    /**
     * Return an initialized [ConfigManager] object. Depending on the given [resolveContext] flag, the manager is
     * either using the resolved context (if the value is *false*) or the original context (if the value is *true*).
     * The original context is only used initially to resolve it.
     */
    fun configManager(resolveContext: Boolean = false): ConfigManager

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
}
