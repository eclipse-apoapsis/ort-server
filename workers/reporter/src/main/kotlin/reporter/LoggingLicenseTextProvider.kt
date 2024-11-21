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

package reporter

import java.io.File

import kotlin.time.measureTimedValue

import org.ossreviewtoolkit.reporter.DefaultLicenseTextProvider
import org.ossreviewtoolkit.reporter.LicenseTextProvider

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(LoggingLicenseTextProvider::class.java)

/**
 * A [LicenseTextProvider] that delegates to a [DefaultLicenseTextProvider] and logs the time it takes to query the
 * license text file.
 */
class LoggingLicenseTextProvider(private val licenseTextDirectory: File) : LicenseTextProvider {
    private val delegate: DefaultLicenseTextProvider = DefaultLicenseTextProvider(listOf(licenseTextDirectory))

    override fun getLicenseTextReader(licenseId: String): (() -> String)? {
        logger.debug("Get custom license text for '$licenseId' from '${licenseTextDirectory.absolutePath}'.")

        val measurement = measureTimedValue {
            delegate.getLicenseTextReader(licenseId)
        }

        logger.debug("Custom license text for '$licenseId' accessed in ${measurement.duration}.")

        return measurement.value
    }
}
