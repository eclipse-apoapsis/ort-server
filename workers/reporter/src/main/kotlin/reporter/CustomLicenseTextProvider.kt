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

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path

import org.ossreviewtoolkit.reporter.DefaultLicenseTextProvider
import org.ossreviewtoolkit.reporter.LicenseTextProvider

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(CustomLicenseTextProvider::class.java)

/**
 * A specialized [LicenseTextProvider] implementation that is used by the [ReporterRunner] to support efficient access
 * to custom license texts stored in a configuration directory.
 *
 * The [LicenseTextProvider] implementations available in ORT expect that license texts are available in a local
 * folder. For ORT Server, this is not necessarily the case, but depends on the provider for config files. This
 * implementation therefore uses the configuration manager API to check whether a requested license text is available
 * in the configuration. If it is, it obtains the license text from the configuration. Otherwise, it delegates to
 * another [LicenseTextProvider] implementation which searches the default paths used by ORT.
 */
internal class CustomLicenseTextProvider(
    /** The object for accessing the configuration. */
    val configManager: ConfigManager,

    /** The current configuration context for this ORT run. */
    val configurationContext: Context?,

    /** The [Path] to the directory in the configuration containing custom license texts. */
    val licenseTextDir: Path,

    /** A fallback [LicenseTextProvider] to query for license texts not available in the configuration. */
    val wrappedProvider: LicenseTextProvider = DefaultLicenseTextProvider()
) : LicenseTextProvider {
    /** Stores the IDs of the licenses that can be resolved from the config directory. */
    private val knownLicenseTexts by lazy {
        val directoryPrefix = "${licenseTextDir.path}/"
        configManager.listFiles(configurationContext, licenseTextDir)
            .map { it.path.removePrefix(directoryPrefix) }
            .also { logger.debug("Found custom license texts: {}.", it) }
    }

    override fun getLicenseTextReader(licenseId: String): (() -> String)? = {
        logger.debug("Request for license text of '{}'.", licenseId)

        if (isKnownLicense(licenseId)) {
            logger.debug("Loading license text of '{}' from config directory.", licenseId)
            getLicenseTextFromConfig(licenseId)
        } else {
            wrappedProvider.getLicenseTextReader(licenseId)?.invoke().orEmpty()
        }
    }

    override fun hasLicenseText(licenseId: String): Boolean {
        return isKnownLicense(licenseId) || wrappedProvider.hasLicenseText(licenseId)
    }

    /**
     * Check whether the given [licenseId] can be resolved from the configuration.
     */
    private fun isKnownLicense(licenseId: String): Boolean =
        licenseId in knownLicenseTexts

    /**
     * Return the license text for the given [licenseId] from the configuration.
     */
    private fun getLicenseTextFromConfig(licenseId: String): String =
        configManager.getFileAsString(configurationContext, Path("${licenseTextDir.path}/$licenseId"))
}
