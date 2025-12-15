/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.workers.evaluator

import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.model.EvaluatorJobConfiguration
import org.eclipse.apoapsis.ortserver.services.config.AdminConfigService
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToOrt
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.readConfigFileValueWithDefault
import org.eclipse.apoapsis.ortserver.workers.common.resolvedConfigurationContext

import org.ossreviewtoolkit.evaluator.Evaluator
import org.ossreviewtoolkit.model.EvaluatorRun
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.LicenseFilePatterns
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.licenses.DefaultLicenseInfoProvider
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.model.utils.FileArchiver
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.CompositePackageConfigurationProvider
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProviderFactory
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.SimplePackageConfigurationProvider
import org.ossreviewtoolkit.utils.config.ConfigurationResolver
import org.ossreviewtoolkit.utils.config.setPackageConfigurations
import org.ossreviewtoolkit.utils.ort.ORT_COPYRIGHT_GARBAGE_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_LICENSE_CLASSIFICATIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(EvaluatorRunner::class.java)

class EvaluatorRunner(
    /**
     * The file archiver is used to resolve license files which is optional input for the rules.
     */
    private val fileArchiver: FileArchiver,

    /** The service for obtaining the admin configuration. */
    private val adminConfigService: AdminConfigService
) {
    /**
     * Invoke the [Evaluator] for the current ORT run.
     * The rule set script and the license classifications file are downloaded from the configuration using the
     * respective paths specified in [config]. In case the path to the license classifications file is not provided,
     * an empty [LicenseClassifications] is passed to the Evaluator.
     */
    suspend fun run(
        ortResult: OrtResult,
        config: EvaluatorJobConfiguration,
        workerContext: WorkerContext
    ): EvaluatorRunnerResult {
        val ruleSetName = workerContext.ortRun.resolvedJobConfigs?.ruleSet
        if (ruleSetName == null) {
            logger.info("Using default rule set for evaluation.")
        } else {
            logger.info("Using rule set '{}' for evaluation.", ruleSetName)
        }

        logger.info("")
        val ruleSet = adminConfigService.loadAdminConfig(
            workerContext.resolvedConfigurationContext,
            workerContext.ortRun.organizationId
        ).getRuleSet(ruleSetName)

        val script = workerContext.configManager.getFileAsString(
            workerContext.resolvedConfigurationContext,
            Path(ruleSet.evaluatorRules)
        )

        val copyrightGarbage = workerContext.configManager.readConfigFileValueWithDefault(
            path = ruleSet.copyrightGarbageFile,
            defaultPath = ORT_COPYRIGHT_GARBAGE_FILENAME,
            fallbackValue = CopyrightGarbage(),
            workerContext.resolvedConfigurationContext
        )

        val licenseClassifications = workerContext.configManager.readConfigFileValueWithDefault(
            path = ruleSet.licenseClassificationsFile,
            defaultPath = ORT_LICENSE_CLASSIFICATIONS_FILENAME,
            fallbackValue = LicenseClassifications(),
            workerContext.resolvedConfigurationContext
        )

        val packageConfigurationProvider = buildList {
            val repositoryPackageConfigurations = ortResult.repository.config.packageConfigurations
            add(SimplePackageConfigurationProvider(configurations = repositoryPackageConfigurations))

            val packageConfigurationProviderConfigs = workerContext
                .resolveProviderPluginConfigSecrets(config.packageConfigurationProviders)
                .map { it.mapToOrt() }

            addAll(PackageConfigurationProviderFactory.create(packageConfigurationProviderConfigs).map { it.second })
        }.let { CompositePackageConfigurationProvider(it) }

        val resolvedOrtResult = ortResult.setPackageConfigurations(packageConfigurationProvider)

        val resolutionsFromOrtResult = resolvedOrtResult.repository.config.resolutions

        val resolutionsFromFile = workerContext.configManager.readConfigFileValueWithDefault(
            path = ruleSet.resolutionsFile,
            defaultPath = ORT_RESOLUTIONS_FILENAME,
            fallbackValue = Resolutions(),
            workerContext.resolvedConfigurationContext
        )

        val resolutionProvider = DefaultResolutionProvider(resolutionsFromOrtResult.merge(resolutionsFromFile))

        // TODO: Make the hardcoded values below configurable.
        val licenseInfoResolver = LicenseInfoResolver(
            provider = DefaultLicenseInfoProvider(resolvedOrtResult),
            copyrightGarbage = copyrightGarbage,
            addAuthorsToCopyrights = true,
            archiver = fileArchiver,
            licenseFilePatterns = LicenseFilePatterns.DEFAULT
        )

        val evaluator = Evaluator(
            ortResult = resolvedOrtResult,
            licenseClassifications = licenseClassifications,
            licenseInfoResolver = licenseInfoResolver,
            resolutionProvider = resolutionProvider
        )

        val evaluatorRun = evaluator.run(script)

        val resolutions = ConfigurationResolver.resolveResolutions(
            issues = resolvedOrtResult.getIssues().values.flatten(),
            ruleViolations = evaluatorRun.violations,
            vulnerabilities = resolvedOrtResult.getVulnerabilities().values.flatten(),
            resolutionProvider = resolutionProvider
        )

        return EvaluatorRunnerResult(
            evaluatorRun,
            resolvedOrtResult.resolvedConfiguration.packageConfigurations.orEmpty(),
            resolutions
        )
    }
}

data class EvaluatorRunnerResult(
    val evaluatorRun: EvaluatorRun,
    val packageConfigurations: List<PackageConfiguration>,
    val resolutions: Resolutions
)
