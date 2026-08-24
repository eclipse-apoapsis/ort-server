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

package org.eclipse.apoapsis.ortserver.components.adminconfig

import org.eclipse.apoapsis.ortserver.model.SourceCodeOrigin

import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration

/**
 * A default scanner configuration instance from ORT. This is used to obtain default values for undefined
 * properties.
 */
private val ortDefaultScannerConfig = ScannerConfiguration()

/**
 * A default downloader configuration instance from ORT. This is used to obtain default values for undefined
 * properties related to the downloader.
 */
private val ortDefaultDownloaderConfig = DownloaderConfiguration()

/**
 * A data class that represents the configuration of the Scanner worker.
 *
 * An instance of this class is part of the [AdminConfig]. It collects a number of properties that configure the
 * Scanner and cannot be set by end users when triggering a run.
 */
data class ScannerConfig(
    /** Mappings from licenses returned by a scanner to valid SPDX licenses. */
    val detectedLicenseMappings: Map<String, String> = ortDefaultScannerConfig.detectedLicenseMapping,

    /** A list of glob expressions that match file paths which are to be excluded from scan results. */
    val ignorePatterns: List<String> = ortDefaultScannerConfig.ignorePatterns,

    /** The source code origins to use, ordered by priority. The list must not be empty or contain any duplicates. */
    val sourceCodeOrigins: List<SourceCodeOrigin> =
        ortDefaultDownloaderConfig.sourceCodeOrigins.map { SourceCodeOrigin.valueOf(it.name) }
) {
    /**
     * Validate this [ScannerConfig] and return a list of found issues. If the returned list is empty, this config is
     * valid.
     */
    fun validate(): List<String> =
        buildList {
            if (sourceCodeOrigins.isEmpty()) {
                add("'sourceCodeOrigins' from scanner configuration must not be empty.")
            }

            if (sourceCodeOrigins.toSet().size != sourceCodeOrigins.size) {
                add(
                    "'sourceCodeOrigins' from scanner configuration must not contain duplicates. " +
                            "Current value is $sourceCodeOrigins."
                )
            }
        }
}
