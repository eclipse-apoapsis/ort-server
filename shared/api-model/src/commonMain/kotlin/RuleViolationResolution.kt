/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.shared.apimodel

import kotlinx.serialization.Serializable

/**
 * Defines the resolution of a rule violation.
 */
@Serializable
data class RuleViolationResolution(
    /** A regular expression to match the rule violation message. */
    val message: String,

    /** The stable identifier for a server-managed rule violation resolution. Null for non-server resolutions. */
    val messageHash: String? = null,

    /** The reason why the rule violation is resolved. */
    val reason: RuleViolationResolutionReason,

    /** A comment to further explain why the [reason] is applicable here. */
    val comment: String,

    /** The source of this resolution. */
    val source: ResolutionSource
)
