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

import org.ossreviewtoolkit.utils.ort.Environment

val defaultReportFormats = listOf(
    "cyclonedx",
    "ortresult",
    "runstatistics",
    "spdxdocument",
    "webapp"
)

// Add default report formats in case no formats are specified.
val resolvedReporterJobConfig = context.ortRun.jobConfigs.reporter?.let {
    when {
        it.formats.isEmpty() -> it.copy(formats = defaultReportFormats)
        else -> it
    }
}

// Disable the notifier job as the notifier-worker is currently not configured in Docker Compose.
var resolvedJobConfigs = context.ortRun.jobConfigs.copy(reporter = resolvedReporterJobConfig, notifier = null)

// Configure a version range for stored ScanCode results.
context.ortRun.jobConfigs.scanner?.let { scannerJobConfig ->
    val scanCodeConfig = (scannerJobConfig.config?.get("ScanCode") ?: PluginConfig(emptyMap(), emptyMap())).let {
        it.copy(options = it.options + mapOf("minVersion" to "32.2.1", "maxVersion" to "33.0.0"))
    }
    val oldConfig = scannerJobConfig.config.orEmpty()
    resolvedJobConfigs = resolvedJobConfigs.copy(
        scanner = scannerJobConfig.copy(config = oldConfig + ("ScanCode" to scanCodeConfig))
    )
}

validationResult = ConfigValidationResultSuccess(
    resolvedConfigurations = resolvedJobConfigs,
    labels = mapOf(
        "createdBy" to "ORT Server $ORT_SERVER_VERSION running ORT ${Environment.ORT_VERSION} in Docker Compose."
    )
)
