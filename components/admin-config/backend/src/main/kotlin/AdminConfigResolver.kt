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

/** A class to resolve an [AdminConfigFile] to an [AdminConfig]. */
internal object AdminConfigResolver {
    /**
     *  Resolve the raw config data from an [AdminConfigFile] to an [AdminConfig] by applying default values and
     *  post-processing of values.
     */
    fun resolve(config: AdminConfigFile): AdminConfig =
        AdminConfig(
            notifierConfig = config.notifier,
            reporterConfig = resolve(config.reporter),
            scannerConfig = config.scanner,
            defaultRuleSet = config.defaultRuleSet,
            ruleSets = config.ruleSets,
            mavenCentralMirror = config.mavenCentralMirror
        )

    private fun resolve(config: ReporterConfigTemplate?): ReporterConfig {
        if (config == null) return AdminConfig.DEFAULT_REPORTER_CONFIG

        return ReporterConfig(
            reportDefinitionsMap = config.reports.orEmpty(),
            howToFixTextProviderFile = config.howToFixTextProviderFile,
            customLicenseTextDir = config.customLicenseTextDir,
            globalAssets = config.assets
        )
    }
}
