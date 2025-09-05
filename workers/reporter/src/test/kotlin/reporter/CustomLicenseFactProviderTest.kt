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

package org.eclipse.apoapsis.ortserver.workers.reporter

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path

class CustomLicenseFactProviderTest : WordSpec({
    "getLicenseText" should {
        "return a license from the config directory" {
            val configManager = createConfigManagerMock()
            every {
                configManager.getFileAsString(configContext, Path("${licenseDir.path}/$LICENSE_ID"))
            } returns LICENSE_TEXT

            val provider = CustomLicenseFactProvider(configManager, configContext, licenseDir)
            val licenseText = provider.getLicenseText(LICENSE_ID)

            licenseText shouldBe LICENSE_TEXT
        }

        "handle a license text dir with a trailing slash" {
            val configManager = createConfigManagerMock()
            every {
                configManager.getFileAsString(configContext, Path("${licenseDir.path}/$LICENSE_ID"))
            } returns LICENSE_TEXT

            val provider = CustomLicenseFactProvider(configManager, configContext, Path("${licenseDir.path}/"))
            val licenseText = provider.getLicenseText(LICENSE_ID)

            licenseText shouldBe LICENSE_TEXT
        }
    }

    "hasLicenseText" should {
        "return true if the license is available from the config repository" {
            val provider = CustomLicenseFactProvider(createConfigManagerMock(), configContext, licenseDir)

            provider.hasLicenseText(LICENSE_ID) shouldBe true
        }
    }
})

/** A test license ID that can be resolved by the config manager. */
private const val LICENSE_ID = "LicenseRef-My-Custom-License"

/** A test custom license text. */
private const val LICENSE_TEXT = "This is a custom license text."

/** The path that contains the custom license texts. */
private val licenseDir = Path("path/to/custom/licenses")

/** The current configuration context. */
private val configContext = Context("theCurrentContext")

/**
 * Return a mock for the [ConfigManager] that is prepared to list the content of the directory with custom license
 * files. It returns a list containing only the test license ID.
 */
private fun createConfigManagerMock(): ConfigManager =
    mockk {
        every { listFiles(configContext, licenseDir) } returns setOf(Path("${licenseDir.path}/$LICENSE_ID"))
    }
