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

import org.eclipse.apoapsis.ortserver.model.runs.repository.RuleViolationResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.RuleViolationResolutionReason
import org.eclipse.apoapsis.ortserver.shared.apimodel.RuleViolationResolution as ApiRuleViolationResolution
import org.eclipse.apoapsis.ortserver.shared.apimodel.RuleViolationResolutionReason as ApiRuleViolationResolutionReason

fun RuleViolationResolution.mapToApi() = ApiRuleViolationResolution(
    message = message,
    messageHash = messageHash,
    reason = reason.mapToApi(),
    comment = comment,
    source = source.mapToApi()
)

fun RuleViolationResolutionReason.mapToApi() = when (this) {
    RuleViolationResolutionReason.CANT_FIX_EXCEPTION -> ApiRuleViolationResolutionReason.CANT_FIX_EXCEPTION

    RuleViolationResolutionReason.DYNAMIC_LINKAGE_EXCEPTION ->
        ApiRuleViolationResolutionReason.DYNAMIC_LINKAGE_EXCEPTION

    RuleViolationResolutionReason.EXAMPLE_OF_EXCEPTION -> ApiRuleViolationResolutionReason.EXAMPLE_OF_EXCEPTION

    RuleViolationResolutionReason.LICENSE_ACQUIRED_EXCEPTION ->
        ApiRuleViolationResolutionReason.LICENSE_ACQUIRED_EXCEPTION

    RuleViolationResolutionReason.NOT_MODIFIED_EXCEPTION -> ApiRuleViolationResolutionReason.NOT_MODIFIED_EXCEPTION

    RuleViolationResolutionReason.PATENT_GRANT_EXCEPTION -> ApiRuleViolationResolutionReason.PATENT_GRANT_EXCEPTION
}

fun ApiRuleViolationResolutionReason.mapToModel() = when (this) {
    ApiRuleViolationResolutionReason.CANT_FIX_EXCEPTION -> RuleViolationResolutionReason.CANT_FIX_EXCEPTION

    ApiRuleViolationResolutionReason.DYNAMIC_LINKAGE_EXCEPTION ->
        RuleViolationResolutionReason.DYNAMIC_LINKAGE_EXCEPTION

    ApiRuleViolationResolutionReason.EXAMPLE_OF_EXCEPTION -> RuleViolationResolutionReason.EXAMPLE_OF_EXCEPTION

    ApiRuleViolationResolutionReason.LICENSE_ACQUIRED_EXCEPTION ->
        RuleViolationResolutionReason.LICENSE_ACQUIRED_EXCEPTION

    ApiRuleViolationResolutionReason.NOT_MODIFIED_EXCEPTION -> RuleViolationResolutionReason.NOT_MODIFIED_EXCEPTION

    ApiRuleViolationResolutionReason.PATENT_GRANT_EXCEPTION -> RuleViolationResolutionReason.PATENT_GRANT_EXCEPTION
}
