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

package org.eclipse.apoapsis.ortserver.workers.common.resolutions

import com.github.michaelbull.result.get

import org.eclipse.apoapsis.ortserver.components.resolutions.vulnerabilities.VulnerabilityResolutionService
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedItemsResult
import org.eclipse.apoapsis.ortserver.model.runs.repository.ResolutionSource
import org.eclipse.apoapsis.ortserver.services.config.AdminConfigService
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToModel
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToOrt
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.readConfigFileValueWithDefault
import org.eclipse.apoapsis.ortserver.workers.common.resolvedConfigurationContext

import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.utils.ResolutionProvider
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME

/**
 * An implementation of [ResolutionProvider] that combines the [Resolutions] from different [sources][ResolutionSource].
 */
class OrtServerResolutionProvider(
    /** The [Resolutions] from the global configuration file. */
    private val globalResolutions: Resolutions,

    /** The [Resolutions] from the repository configuration file. */
    private val repositoryConfigurationResolutions: Resolutions,

    /** The [Resolutions] from the repository managed by the server. */
    private val managedResolutions: Resolutions
) : ResolutionProvider {
    private val allResolutions =
        globalResolutions.merge(repositoryConfigurationResolutions).merge(managedResolutions)

    companion object {
        /**
         * Create a new instance of [OrtServerResolutionProvider]. The global [Resolutions] file is loaded using the
         * [context] and [adminConfigService]. Resolutions from the repository configuration file are passed as
         * [repositoryConfigurationResolutions]. Resolutions defined in the server [repository][repositoryId] are
         * loaded using the [vulnerabilityResolutionService].
         *
         * The [vulnerabilityResolutionService] is optional, as it is only required by the advisor worker which is the
         * only one adding vulnerabilitie to the run.
         */
        fun create(
            context: WorkerContext,
            adminConfigService: AdminConfigService,
            repositoryConfigurationResolutions: Resolutions,
            repositoryId: RepositoryId,
            vulnerabilityResolutionService: VulnerabilityResolutionService? = null
        ): OrtServerResolutionProvider {
            val globalResolutions = context.loadGlobalResolutions(adminConfigService)
            val repositoryResolutions = Resolutions(
                vulnerabilities = vulnerabilityResolutionService?.getResolutionsForRepository(repositoryId)
                    ?.get().orEmpty().map { it.mapToOrt() }
            )

            return OrtServerResolutionProvider(
                globalResolutions = globalResolutions,
                repositoryConfigurationResolutions = repositoryConfigurationResolutions,
                managedResolutions = repositoryResolutions
            )
        }

        /**
         * Return the global [Resolutions] loaded from the resolutions file.
         */
        private fun WorkerContext.loadGlobalResolutions(adminConfigService: AdminConfigService): Resolutions {
            val adminConfig = adminConfigService.loadAdminConfig(
                resolvedConfigurationContext,
                ortRun.organizationId
            )
            val ruleSet = adminConfig.getRuleSet(ortRun.resolvedJobConfigs?.ruleSet)

            return configManager.readConfigFileValueWithDefault(
                ruleSet.resolutionsFile,
                ORT_RESOLUTIONS_FILENAME,
                Resolutions(),
                resolvedConfigurationContext
            )
        }
    }

    override fun getResolutionsFor(issue: Issue) =
        allResolutions.issues.filter { it.matches(issue) }

    override fun getResolutionsFor(violation: RuleViolation) =
        allResolutions.ruleViolations.filter { it.matches(violation) }

    override fun getResolutionsFor(vulnerability: Vulnerability) =
        allResolutions.vulnerabilities.filter { it.matches(vulnerability) }

    /**
     * Return a [ResolvedItemsResult] that maps the provided [issues], [ruleViolations], and [vulnerabilities] to their
     * matching resolutions from this provider. This is similar to ORT's `ConfigurationResolver.resolveResolutions()`,
     * but returns the full mappings instead of just the distinct resolutions.
     */
    fun matchResolutions(
        issues: List<Issue>,
        ruleViolations: List<RuleViolation>,
        vulnerabilities: List<Vulnerability>
    ): ResolvedItemsResult {
        val issueResolutions = issues
            .associateWith { getResolutionsFor(it) }
            .filterValues { it.isNotEmpty() }

        val ruleViolationResolutions = ruleViolations
            .associateWith { getResolutionsFor(it) }
            .filterValues { it.isNotEmpty() }

        val vulnerabilityResolutions = vulnerabilities
            .associateWith { getResolutionsFor(it) }
            .filterValues { it.isNotEmpty() }

        return ResolvedItemsResult(
            issues = issueResolutions.map { (issue, resolutions) ->
                issue.mapToModel() to resolutions.map { it.mapToModel(ResolutionSource.REPOSITORY_FILE) }
            }.toMap(),
            ruleViolations = ruleViolationResolutions.map { (violation, resolutions) ->
                violation.mapToModel() to resolutions.map { it.mapToModel(ResolutionSource.REPOSITORY_FILE) }
            }.toMap(),
            vulnerabilities = vulnerabilityResolutions.map { (vulnerability, resolutions) ->
                vulnerability.mapToModel() to resolutions.map { it.mapToModel(ResolutionSource.REPOSITORY_FILE) }
            }.toMap()
        )
    }
}
