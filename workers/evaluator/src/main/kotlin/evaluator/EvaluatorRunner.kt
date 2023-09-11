/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.evaluator

import org.ossreviewtoolkit.evaluator.Evaluator
import org.ossreviewtoolkit.model.EvaluatorRun
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.LicenseFilePatterns
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.licenses.DefaultLicenseInfoProvider
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.utils.CompositePackageConfigurationProvider
import org.ossreviewtoolkit.model.utils.ConfigurationResolver
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProviderFactory
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.SimplePackageConfigurationProvider
import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.config.Path
import org.ossreviewtoolkit.server.model.EvaluatorJobConfiguration
import org.ossreviewtoolkit.server.workers.common.mapToOrt

class EvaluatorRunner(
    /**
     * The config manager is used to download the rule script as well as the file describing license classifications.
     */
    private val configManager: ConfigManager
) {
    /**
     * The rule set script and the license classifications file are obtained from the [configManager] using the
     * respective paths specified in [config]. In case the path to the license classifications file is not provided,
     * an empty [LicenseClassifications] is passed to the Evaluator.
     */
    fun run(ortResult: OrtResult, config: EvaluatorJobConfiguration): EvaluatorRunnerResult {
        val script = config.ruleSet?.let { configManager.getFileAsString(null, Path(it)) }
            ?: throw IllegalArgumentException("The rule set path is not specified in the config.", null)

        val licenseClassifications = config.licenseClassificationsFile?.let {
            configManager.getFile(null, Path(it)).use { rawLicenseClassifications ->
                yamlMapper.readValue(rawLicenseClassifications, LicenseClassifications::class.java)
            }
        } ?: LicenseClassifications()

        val packageConfigurationProvider = buildList {
            val repositoryPackageConfigurations = ortResult.repository.config.packageConfigurations
            add(SimplePackageConfigurationProvider(repositoryPackageConfigurations))

            val packageConfigurationProviderConfigs = config.packageConfigurationProviders.map { it.mapToOrt() }
            addAll(PackageConfigurationProviderFactory.create(packageConfigurationProviderConfigs).map { it.second })
        }.let { CompositePackageConfigurationProvider(it) }

        // TODO: Make the hardcoded values below configurable.
        val licenseInfoResolver = LicenseInfoResolver(
            provider = DefaultLicenseInfoProvider(ortResult, packageConfigurationProvider),
            copyrightGarbage = CopyrightGarbage(),
            addAuthorsToCopyrights = true,
            archiver = null,
            licenseFilePatterns = LicenseFilePatterns.DEFAULT
        )

        val evaluator = Evaluator(
            ortResult = ortResult,
            licenseClassifications = licenseClassifications,
            licenseInfoResolver = licenseInfoResolver
        )

        val evaluatorRun = evaluator.run(script)

        val packageConfigurations = ConfigurationResolver.resolvePackageConfigurations(
            identifiers = ortResult.getUncuratedPackages().mapTo(mutableSetOf()) { it.id },
            scanResultProvider = { id -> ortResult.getScanResultsForId(id) },
            packageConfigurationProvider = packageConfigurationProvider
        )

        return EvaluatorRunnerResult(evaluatorRun, packageConfigurations)
    }
}

data class EvaluatorRunnerResult(
    val evaluatorRun: EvaluatorRun,
    val packageConfigurations: List<PackageConfiguration>
)
