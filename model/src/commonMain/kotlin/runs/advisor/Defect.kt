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

package org.eclipse.apoapsis.ortserver.model.runs.advisor

import kotlin.time.Instant

data class Defect(
    val externalId: String,
    val url: String,
    val title: String?,
    val state: String?,
    val severity: String?,
    val description: String?,
    val creationTime: Instant?,
    val modificationTime: Instant?,
    val closingTime: Instant?,
    val fixReleaseVersion: String?,
    val fixReleaseUrl: String?,
    val labels: Map<String, String>
)
