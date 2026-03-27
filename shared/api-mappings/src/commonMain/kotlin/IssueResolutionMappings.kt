/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.shared.apimappings

import org.eclipse.apoapsis.ortserver.model.runs.repository.AppliedIssueResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolutionReason
import org.eclipse.apoapsis.ortserver.shared.apimodel.AppliedIssueResolution as ApiAppliedIssueResolution
import org.eclipse.apoapsis.ortserver.shared.apimodel.IssueResolution as ApiIssueResolution
import org.eclipse.apoapsis.ortserver.shared.apimodel.IssueResolutionReason as ApiIssueResolutionReason

fun IssueResolution.mapToApi() = ApiIssueResolution(
    message = message,
    messageHash = messageHash,
    reason = reason.mapToApi(),
    comment = comment,
    source = source.mapToApi()
)

fun AppliedIssueResolution.mapToApi() = ApiAppliedIssueResolution(
    message = message,
    messageHash = messageHash,
    reason = reason.mapToApi(),
    comment = comment,
    source = source.mapToApi(),
    isDeleted = isDeleted
)

fun IssueResolutionReason.mapToApi() = when (this) {
    IssueResolutionReason.BUILD_TOOL_ISSUE -> ApiIssueResolutionReason.BUILD_TOOL_ISSUE
    IssueResolutionReason.CANT_FIX_ISSUE -> ApiIssueResolutionReason.CANT_FIX_ISSUE
    IssueResolutionReason.SCANNER_ISSUE -> ApiIssueResolutionReason.SCANNER_ISSUE
}

fun ApiIssueResolutionReason.mapToModel() = when (this) {
    ApiIssueResolutionReason.BUILD_TOOL_ISSUE -> IssueResolutionReason.BUILD_TOOL_ISSUE
    ApiIssueResolutionReason.CANT_FIX_ISSUE -> IssueResolutionReason.CANT_FIX_ISSUE
    ApiIssueResolutionReason.SCANNER_ISSUE -> IssueResolutionReason.SCANNER_ISSUE
}

fun ApiAppliedIssueResolution.mapToModel() = AppliedIssueResolution(
    message = message,
    messageHash = messageHash,
    reason = reason.mapToModel(),
    comment = comment,
    source = source.mapToModel(),
    isDeleted = isDeleted
)
