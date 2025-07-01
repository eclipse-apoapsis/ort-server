/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.model.runs

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolution

/**
 * A data class describing an issue that occurred during an ORT run.
 */
@Serializable
data class Issue(
    /** The timestamp when this issue occurred. */
    val timestamp: Instant,

    /** The source where this issue occurred. */
    val source: String,

    /** A message describing the issue. */
    val message: String,

    /** The [Severity] of the issue. */
    val severity: Severity,

    /** The optional file path this issue is related to. */
    val affectedPath: String? = null,

    /** An optional identifier of a [Package] this issue is related to. */
    val identifier: Identifier? = null,

    /** The worker which caused this issue if available. */
    val worker: String? = null,

    /** The [IssueResolution]s that have been applied to this issue. */
    val resolutions: List<IssueResolution> = emptyList()
)
