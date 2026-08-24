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

package org.eclipse.apoapsis.ortserver.components.adminconfig

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.should

import org.eclipse.apoapsis.ortserver.model.SourceCodeOrigin

class ScannerConfigTest : WordSpec({
    "validate" should {
        "return an issue if the list of source code origins is empty" {
            ScannerConfig(sourceCodeOrigins = emptyList()).validate() should haveSize(1)
        }

        "return an issue if the list of source code origins contains duplicates" {
            ScannerConfig(
                sourceCodeOrigins = listOf(SourceCodeOrigin.VCS, SourceCodeOrigin.ARTIFACT, SourceCodeOrigin.VCS)
            ).validate() should haveSize(1)
        }
    }
})
