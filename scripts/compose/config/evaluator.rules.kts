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

val ruleSet = ruleSet(ortResult, licenseInfoResolver, resolutionProvider) {
    packageRule("NO_LICENSE_IN_DEPENDENCY") {
        require {
            -isProject()
            -isExcluded()
            -hasLicense()
        }

        error(
            message = "No license information found for dependency '${pkg.metadata.id.toCoordinates()}'.",
            howToFix = "Use a package curation to assign a concluded license to the package."
        )
    }
}

ruleViolations += ruleSet.violations
