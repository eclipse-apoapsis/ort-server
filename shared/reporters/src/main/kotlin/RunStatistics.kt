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

package org.eclipse.apoapsis.ortserver.shared.reporters

import java.util.SortedMap
import java.util.SortedSet

import org.ossreviewtoolkit.reporter.DependencyTreeStatistics
import org.ossreviewtoolkit.reporter.IssueStatistics
import org.ossreviewtoolkit.reporter.LicenseStatistics
import org.ossreviewtoolkit.reporter.RepositoryConfigurationStatistics
import org.ossreviewtoolkit.reporter.ResolvedConfigurationStatistics
import org.ossreviewtoolkit.reporter.Statistics

/**
 * The statistics of an ORT run as exported by the [RunStatisticsReporter]. This model is decoupled from ORT's
 * [Statistics] class to keep the format of the produced report stable, independent of changes in ORT.
 */
data class RunStatistics(
    val repositoryConfiguration: RunRepositoryConfigurationStatistics,
    val resolvedConfiguration: RunResolvedConfigurationStatistics,
    val openIssues: RunIssueStatistics,
    val openRuleViolations: RunIssueStatistics,
    val openVulnerabilities: Int,
    val dependencyTree: RunDependencyTreeStatistics,
    val licenses: RunLicenseStatistics,
    val executionDurationInSeconds: Long
)

data class RunIssueStatistics(
    val errors: Int,
    val warnings: Int,
    val hints: Int,
    val severe: Int
)

data class RunDependencyTreeStatistics(
    val includedProjects: Int,
    val excludedProjects: Int,
    val includedPackages: Int,
    val excludedPackages: Int,
    val includedScopes: SortedSet<String>,
    val excludedScopes: SortedSet<String>,
    val totalTreeDepth: Int,
    val includedTreeDepth: Int
)

data class RunLicenseStatistics(
    val declared: SortedMap<String, Int>,
    val detected: SortedMap<String, Int>,
    val effective: SortedMap<String, Int>
)

data class RunRepositoryConfigurationStatistics(
    val pathExcludes: Int,
    val scopeExcludes: Int,
    val licenseChoices: Int,
    val licenseFindingCurations: Int,
    val packageConfigurations: Int,
    val packageCurations: Int,
    val issueResolutions: Int,
    val ruleViolationResolutions: Int,
    val vulnerabilityResolutions: Int
)

data class RunResolvedConfigurationStatistics(
    val licenseChoices: Int,
    val packageConfigurations: Int,
    val packageCurations: Int,
    val issueResolutions: Int,
    val ruleViolationResolutions: Int,
    val vulnerabilityResolutions: Int
)

/**
 * Convert ORT's [Statistics] to [RunStatistics].
 */
fun Statistics.toRunStatistics() =
    RunStatistics(
        repositoryConfiguration = repositoryConfiguration.toRunStatistics(),
        resolvedConfiguration = resolvedConfiguration.toRunStatistics(),
        openIssues = openIssues.toRunStatistics(),
        openRuleViolations = openRuleViolations.toRunStatistics(),
        openVulnerabilities = openVulnerabilities,
        dependencyTree = dependencyTree.toRunStatistics(),
        licenses = licenses.toRunStatistics(),
        executionDurationInSeconds = executionDurationInSeconds
    )

private fun IssueStatistics.toRunStatistics() =
    RunIssueStatistics(
        errors = errors,
        warnings = warnings,
        hints = hints,
        severe = severe
    )

private fun DependencyTreeStatistics.toRunStatistics() =
    RunDependencyTreeStatistics(
        includedProjects = includedProjects,
        excludedProjects = excludedProjects,
        includedPackages = includedPackages,
        excludedPackages = excludesPackages,
        includedScopes = includedScopes.toSortedSet(),
        excludedScopes = excludedScopes.toSortedSet(),
        totalTreeDepth = totalTreeDepth,
        includedTreeDepth = includedTreeDepth
    )

private fun LicenseStatistics.toRunStatistics() =
    RunLicenseStatistics(
        declared = declared.toSortedMap(),
        detected = detected.toSortedMap(),
        effective = effective.toSortedMap()
    )

private fun RepositoryConfigurationStatistics.toRunStatistics() =
    RunRepositoryConfigurationStatistics(
        pathExcludes = pathExcludes,
        scopeExcludes = scopeExcludes,
        licenseChoices = licenseChoices,
        licenseFindingCurations = licenseFindingCurations,
        packageConfigurations = packageConfigurations,
        packageCurations = packageCurations,
        issueResolutions = issueResolutions,
        ruleViolationResolutions = ruleViolationResolutions,
        vulnerabilityResolutions = vulnerabilityResolutions
    )

private fun ResolvedConfigurationStatistics.toRunStatistics() =
    RunResolvedConfigurationStatistics(
        licenseChoices = licenseChoices,
        packageConfigurations = packageConfigurations,
        packageCurations = packageCurations,
        issueResolutions = issueResolutions,
        ruleViolationResolutions = ruleViolationResolutions,
        vulnerabilityResolutions = vulnerabilityResolutions
    )
