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

package org.eclipse.apoapsis.ortserver.model.runs.reporter

import kotlin.time.Instant

/**
 * A data class providing some metadata about a report generated during an ORT run.
 */
data class Report(
    /** The filename of the report. */
    val filename: String,

    /** A link that can be used to download the report without additional authentication. */
    val downloadLink: String,

    /** The date until when the [downloadLink] is valid. */
    val downloadTokenExpiryDate: Instant,

    /** The size of the report file in bytes, or null if unknown (legacy data). */
    val sizeInBytes: Long? = null
)
