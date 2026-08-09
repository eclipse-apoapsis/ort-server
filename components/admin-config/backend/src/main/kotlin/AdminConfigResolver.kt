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

import org.eclipse.apoapsis.ortserver.components.adminconfig.AdminConfigService.Companion.UNRESOLVABLE_ASSET_PREFIX
import org.eclipse.apoapsis.ortserver.model.SourceCodeOrigin

/** A class to resolve an [AdminConfigFile] to an [AdminConfig]. */
internal object AdminConfigResolver {
    /**
     *  Resolve the raw config data from an [AdminConfigFile] to an [AdminConfig] by applying default values and
     *  post-processing of values.
     */
    fun resolve(config: AdminConfigFile): AdminConfig {
        val defaultRuleSet = resolve(config.defaultRuleSet, AdminConfig.DEFAULT_RULE_SET)

        return AdminConfig(
            notifierConfig = config.notifier,
            reporterConfig = resolve(config.reporter),
            scannerConfig = resolve(config.scanner),
            defaultRuleSet = defaultRuleSet,
            ruleSets = config.ruleSets.orEmpty().mapValues { resolve(it.value, defaultRuleSet) },
            mavenCentralMirror = config.mavenCentralMirror
        )
    }

    private fun resolve(config: ReporterConfigTemplate?): ReporterConfig {
        if (config == null) return AdminConfig.DEFAULT_REPORTER_CONFIG

        val reportDefinitions = config.reports.orEmpty().mapValues { (_, template) ->
            val resolvedAssetFilesRefs = template.assetFilesRefs.flatMap { assetRef ->
                // Replace the reference with the referenced list of asset files from the global assets. If this does
                // not exist, add a placeholder asset that marks the reference as not resolvable.
                config.assets[assetRef] ?: listOf(ReporterAsset(UNRESOLVABLE_ASSET_PREFIX + assetRef))
            }

            val assetDirectories = template.assetDirectories.map {
                it.copy(sourcePath = it.sourcePath.ensureTrailingSlash())
            }

            val resolvedAssetDirectoriesRefs = template.assetDirectoriesRefs.flatMap { assetRef ->
                // Replace the reference with the referenced list of asset directories from the global assets. If this
                // does not exist, add a placeholder asset that marks the reference as not resolvable.
                config.assets[assetRef]?.map {
                    // Ensure that directory asset paths end with a trailing slash.
                    it.copy(sourcePath = it.sourcePath.ensureTrailingSlash())
                } ?: listOf(ReporterAsset(UNRESOLVABLE_ASSET_PREFIX + assetRef))
            }

            ReportDefinition(
                pluginId = template.pluginId,
                assetFiles = template.assetFiles + resolvedAssetFilesRefs,
                assetDirectories = assetDirectories + resolvedAssetDirectoriesRefs,
                nameMapping = template.nameMapping
            )
        }

        return ReporterConfig(
            reportDefinitionsMap = ReporterConfig.addDefinitionsForUnreferencedPlugins(reportDefinitions),
            howToFixTextProviderFile = config.howToFixTextProviderFile,
            customLicenseTextDir = config.customLicenseTextDir,
            globalAssets = config.assets
        )
    }

    private fun resolve(config: ScannerConfigTemplate?): ScannerConfig {
        if (config == null) return AdminConfig.DEFAULT_SCANNER_CONFIG

        return ScannerConfig(
            detectedLicenseMappings = config.detectedLicenseMappings
                ?: AdminConfig.DEFAULT_SCANNER_CONFIG.detectedLicenseMappings,
            ignorePatterns = config.ignorePatterns ?: AdminConfig.DEFAULT_SCANNER_CONFIG.ignorePatterns,
            sourceCodeOrigins = config.sourceCodeOrigins?.map { SourceCodeOrigin.valueOf(it.uppercase()) }
                ?: AdminConfig.DEFAULT_SCANNER_CONFIG.sourceCodeOrigins
        )
    }

    private fun resolve(config: RuleSetTemplate?, default: RuleSet): RuleSet {
        if (config == null) return default

        return RuleSet(
            copyrightGarbageFile = config.copyrightGarbageFile ?: default.copyrightGarbageFile,
            licenseClassificationsFile = config.licenseClassificationsFile ?: default.licenseClassificationsFile,
            resolutionsFile = config.resolutionsFile ?: default.resolutionsFile,
            evaluatorRules = config.evaluatorRules ?: default.evaluatorRules
        )
    }
}

private fun String.ensureTrailingSlash() = takeIf { it.endsWith("/") } ?: plus("/")
