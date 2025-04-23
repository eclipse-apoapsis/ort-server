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

package org.eclipse.apoapsis.ortserver.model.runs.scanner

import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.model.runs.Issue

data class ScanSummary(
    val startTime: Instant,
    val endTime: Instant,

    /**
     * A hash value for this scan summary. The hash is calculated based on all properties and associated entities.
     * It allows detecting if two scan summaries are equal without comparing all properties.
     */
    val hash: String,

    val licenseFindings: Set<LicenseFinding>,
    val copyrightFindings: Set<CopyrightFinding>,
    val snippetFindings: Set<SnippetFinding>,
    val issues: List<Issue> = emptyList()
)
