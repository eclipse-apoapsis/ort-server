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

package org.eclipse.apoapsis.ortserver.workers.scanner

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.shared.orttestdata.OrtTestData

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.LicenseFilePatterns
import org.ossreviewtoolkit.model.licenses.DefaultLicenseInfoProvider
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver

class LicenseComputationTest : StringSpec({
    "computeDetectedLicenses returns curated licenses using OrtTestData" {
        val id = OrtTestData.pkgIdentifier

        val ortResult = OrtTestData.result

        val resolver = LicenseInfoResolver(
            provider = DefaultLicenseInfoProvider(ortResult),
            copyrightGarbage = CopyrightGarbage(),
            addAuthorsToCopyrights = false,
            archiver = mockk(relaxed = true),
            licenseFilePatterns = LicenseFilePatterns.DEFAULT
        )

        val result = computeDetectedLicenses(resolver, id)

        result shouldContain "LicenseRef-detected1-concluded"
        result shouldContain "LicenseRef-detected2"
        result shouldContain "LicenseRef-detected3"
    }

    "computeDetectedLicenses returns all detected licenses when no curations exist" {
        val id = OrtTestData.pkgIdentifier

        val ortResult = OrtTestData.result.copy(
            repository = OrtTestData.repository.copy(
                config = OrtTestData.repository.config.copy(
                    packageConfigurations = emptyList()
                )
            ),
            resolvedConfiguration = OrtTestData.resolvedConfiguration.copy(
                packageConfigurations = emptyList()
            )
        )

        val resolver = LicenseInfoResolver(
            provider = DefaultLicenseInfoProvider(ortResult),
            copyrightGarbage = CopyrightGarbage(),
            addAuthorsToCopyrights = false,
            archiver = mockk(relaxed = true),
            licenseFilePatterns = LicenseFilePatterns.DEFAULT
        )

        val result = computeDetectedLicenses(resolver, id)

        result shouldContain "LicenseRef-detected1"
        result shouldContain "LicenseRef-detected2"
        result shouldContain "LicenseRef-detected3"
        result shouldContain "LicenseRef-detected-excluded"
    }

    "computeDetectedLicenses returns empty set when no scan results for id" {
        val id = Identifier("Maven", "com.example", "no-scan-pkg", "1.0")

        val ortResult = OrtResult.EMPTY

        val resolver = LicenseInfoResolver(
            provider = DefaultLicenseInfoProvider(ortResult),
            copyrightGarbage = CopyrightGarbage(),
            addAuthorsToCopyrights = false,
            archiver = mockk(relaxed = true),
            licenseFilePatterns = LicenseFilePatterns.DEFAULT
        )

        val result = computeDetectedLicenses(resolver, id)

        result.shouldBeEmpty()
    }

    "computeDetectedLicenses deduplicates licenses across provenances" {
        val ortResult = OrtTestData.result.copy(
            repository = OrtTestData.repository.copy(
                config = OrtTestData.repository.config.copy(
                    packageConfigurations = emptyList()
                )
            ),
            resolvedConfiguration = OrtTestData.resolvedConfiguration.copy(
                packageConfigurations = emptyList()
            )
        )
        val id = OrtTestData.pkgIdentifier

        val resolver = LicenseInfoResolver(
            provider = DefaultLicenseInfoProvider(ortResult),
            copyrightGarbage = CopyrightGarbage(),
            addAuthorsToCopyrights = false,
            archiver = mockk(relaxed = true),
            licenseFilePatterns = LicenseFilePatterns.DEFAULT
        )

        val result = computeDetectedLicenses(resolver, id)

        val licenseCounts = result.groupingBy { it }.eachCount()
        licenseCounts.values.all { it == 1 } shouldBe true
    }

    "computeEffectiveLicense should return null when no license info is available" {
        val id = Identifier("Maven", "com.example", "no-license-pkg", "1.0")

        val ortResult = OrtResult.EMPTY
        val resolver = LicenseInfoResolver(
            provider = DefaultLicenseInfoProvider(ortResult),
            copyrightGarbage = CopyrightGarbage(),
            addAuthorsToCopyrights = false,
            archiver = mockk(relaxed = true),
            licenseFilePatterns = LicenseFilePatterns.DEFAULT
        )

        val result = computeEffectiveLicense(resolver, id)

        result.shouldBeNull()
    }
})
