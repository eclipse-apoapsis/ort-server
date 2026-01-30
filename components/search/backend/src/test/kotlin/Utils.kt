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

package ort.eclipse.apoapsis.ortserver.components.search

import kotlin.time.Clock

import org.eclipse.apoapsis.ortserver.api.v1.model.Identifier as ApiIdentifier
import org.eclipse.apoapsis.ortserver.components.search.apimodel.RunWithPackage
import org.eclipse.apoapsis.ortserver.components.search.apimodel.RunWithVulnerability
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.PackageCurationProviderConfig
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedPackageCurations
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorResult
import org.eclipse.apoapsis.ortserver.model.runs.advisor.Vulnerability
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageCuration
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageCurationData

fun createRunWithPackage(
    fixtures: Fixtures,
    repoId: Long = -1L,
    pkgId: Identifier = Identifier("test", "ns", "name", "ver")
): RunWithPackage {
    val pkg = fixtures.generatePackage(pkgId)
    val ortRun = fixtures.createAnalyzerRunWithPackages(packages = setOf(pkg), repositoryId = repoId)

    return RunWithPackage(
        organizationId = ortRun.organizationId,
        productId = ortRun.productId,
        repositoryId = ortRun.repositoryId,
        ortRunId = ortRun.id,
        ortRunIndex = ortRun.index,
        revision = ortRun.revision,
        createdAt = ortRun.createdAt,
        packageId = pkgId.toApiIdentifier(),
        purl = null
    )
}

/**
 * Create a run with a package and add a PURL curation for it.
 * Returns the RunWithPackage with the curated PURL in the purl field (for PURL search testing).
 */
fun createRunWithCuratedPurl(
    fixtures: Fixtures,
    repoId: Long,
    pkgId: Identifier,
    curatedPurl: String
): RunWithPackage {
    val pkg = fixtures.generatePackage(pkgId)
    val ortRun = fixtures.createAnalyzerRunWithPackages(packages = setOf(pkg), repositoryId = repoId)

    val curation = PackageCuration(
        id = pkgId,
        data = PackageCurationData(purl = curatedPurl)
    )

    val resolvedPackageCurations = ResolvedPackageCurations(
        provider = PackageCurationProviderConfig(name = "TestProvider"),
        curations = listOf(curation)
    )

    fixtures.resolvedConfigurationRepository.addPackageCurations(ortRun.id, listOf(resolvedPackageCurations))

    return RunWithPackage(
        organizationId = ortRun.organizationId,
        productId = ortRun.productId,
        repositoryId = ortRun.repositoryId,
        ortRunId = ortRun.id,
        ortRunIndex = ortRun.index,
        revision = ortRun.revision,
        createdAt = ortRun.createdAt,
        packageId = null,
        purl = curatedPurl
    )
}

/**
 * Create a run with a package for PURL search testing (without curation).
 * Returns the RunWithPackage with the original PURL in the purl field.
 */
fun createRunWithPackageForPurlSearch(
    fixtures: Fixtures,
    repoId: Long = -1L,
    pkgId: Identifier = Identifier("test", "ns", "name", "ver")
): RunWithPackage {
    val pkg = fixtures.generatePackage(pkgId)
    val ortRun = fixtures.createAnalyzerRunWithPackages(packages = setOf(pkg), repositoryId = repoId)

    return RunWithPackage(
        organizationId = ortRun.organizationId,
        productId = ortRun.productId,
        repositoryId = ortRun.repositoryId,
        ortRunId = ortRun.id,
        ortRunIndex = ortRun.index,
        revision = ortRun.revision,
        createdAt = ortRun.createdAt,
        packageId = null,
        purl = pkgId.toPurl()
    )
}

fun Identifier.toCoordinates(): String = "$type:$namespace:$name:$version"

fun Identifier.toPurl(): String = "pkg:$type/$namespace/$name@$version"

fun Identifier.toApiIdentifier(): ApiIdentifier = ApiIdentifier(type, namespace, name, version)

/**
 * Create an ORT run with a vulnerability associated with the given package identifier.
 * Returns a RunWithVulnerability with the packageId populated (for returnPurl=false testing).
 */
fun createRunWithVulnerability(
    fixtures: Fixtures,
    repoId: Long,
    pkgId: Identifier,
    vulnerabilityExternalId: String
): RunWithVulnerability {
    val pkg = fixtures.generatePackage(pkgId)

    // Create an analyzer run with the package (so there's a package for purl lookups)
    val ortRun = fixtures.createAnalyzerRunWithPackages(packages = setOf(pkg), repositoryId = repoId)

    // Create an advisor run with the vulnerability
    val advisorJob = fixtures.createAdvisorJob(ortRunId = ortRun.id)
    val vulnerability = Vulnerability(
        externalId = vulnerabilityExternalId,
        summary = "Test vulnerability",
        description = "Description for $vulnerabilityExternalId",
        references = emptyList()
    )
    val advisorResult = AdvisorResult(
        advisorName = "TestAdvisor",
        startTime = Clock.System.now(),
        endTime = Clock.System.now(),
        issues = emptyList(),
        vulnerabilities = listOf(vulnerability)
    )
    fixtures.createAdvisorRun(
        advisorJobId = advisorJob.id,
        results = mapOf(pkgId to listOf(advisorResult))
    )

    return RunWithVulnerability(
        organizationId = ortRun.organizationId,
        productId = ortRun.productId,
        repositoryId = ortRun.repositoryId,
        ortRunId = ortRun.id,
        ortRunIndex = ortRun.index,
        revision = ortRun.revision,
        createdAt = ortRun.createdAt,
        externalId = vulnerabilityExternalId,
        packageId = pkgId.toApiIdentifier(),
        purl = null
    )
}

/**
 * Create an ORT run with a vulnerability and add a PURL curation for the package.
 * Returns a RunWithVulnerability with the curated PURL in the purl field (for returnPurl=true testing).
 */
fun createRunWithVulnerabilityAndCuratedPurl(
    fixtures: Fixtures,
    repoId: Long,
    pkgId: Identifier,
    vulnerabilityExternalId: String,
    curatedPurl: String
): RunWithVulnerability {
    val pkg = fixtures.generatePackage(pkgId)

    // Create an analyzer run with the package
    val ortRun = fixtures.createAnalyzerRunWithPackages(packages = setOf(pkg), repositoryId = repoId)

    // Create an advisor run with the vulnerability
    val advisorJob = fixtures.createAdvisorJob(ortRunId = ortRun.id)
    val vulnerability = Vulnerability(
        externalId = vulnerabilityExternalId,
        summary = "Test vulnerability",
        description = "Description for $vulnerabilityExternalId",
        references = emptyList()
    )
    val advisorResult = AdvisorResult(
        advisorName = "TestAdvisor",
        startTime = Clock.System.now(),
        endTime = Clock.System.now(),
        issues = emptyList(),
        vulnerabilities = listOf(vulnerability)
    )
    fixtures.createAdvisorRun(
        advisorJobId = advisorJob.id,
        results = mapOf(pkgId to listOf(advisorResult))
    )

    // Add PURL curation
    val curation = PackageCuration(
        id = pkgId,
        data = PackageCurationData(purl = curatedPurl)
    )

    val resolvedPackageCurations = ResolvedPackageCurations(
        provider = PackageCurationProviderConfig(name = "TestProvider"),
        curations = listOf(curation)
    )

    fixtures.resolvedConfigurationRepository.addPackageCurations(ortRun.id, listOf(resolvedPackageCurations))

    return RunWithVulnerability(
        organizationId = ortRun.organizationId,
        productId = ortRun.productId,
        repositoryId = ortRun.repositoryId,
        ortRunId = ortRun.id,
        ortRunIndex = ortRun.index,
        revision = ortRun.revision,
        createdAt = ortRun.createdAt,
        externalId = vulnerabilityExternalId,
        packageId = null,
        purl = curatedPurl
    )
}

/**
 * Create an ORT run with a vulnerability for PURL search testing (without curation).
 * Returns a RunWithVulnerability with the original PURL in the purl field.
 */
fun createRunWithVulnerabilityForPurlSearch(
    fixtures: Fixtures,
    repoId: Long,
    pkgId: Identifier,
    vulnerabilityExternalId: String
): RunWithVulnerability {
    val pkg = fixtures.generatePackage(pkgId)

    // Create an analyzer run with the package
    val ortRun = fixtures.createAnalyzerRunWithPackages(packages = setOf(pkg), repositoryId = repoId)

    // Create an advisor run with the vulnerability
    val advisorJob = fixtures.createAdvisorJob(ortRunId = ortRun.id)
    val vulnerability = Vulnerability(
        externalId = vulnerabilityExternalId,
        summary = "Test vulnerability",
        description = "Description for $vulnerabilityExternalId",
        references = emptyList()
    )
    val advisorResult = AdvisorResult(
        advisorName = "TestAdvisor",
        startTime = Clock.System.now(),
        endTime = Clock.System.now(),
        issues = emptyList(),
        vulnerabilities = listOf(vulnerability)
    )
    fixtures.createAdvisorRun(
        advisorJobId = advisorJob.id,
        results = mapOf(pkgId to listOf(advisorResult))
    )

    return RunWithVulnerability(
        organizationId = ortRun.organizationId,
        productId = ortRun.productId,
        repositoryId = ortRun.repositoryId,
        ortRunId = ortRun.id,
        ortRunIndex = ortRun.index,
        revision = ortRun.revision,
        createdAt = ortRun.createdAt,
        externalId = vulnerabilityExternalId,
        packageId = null,
        purl = pkgId.toPurl()
    )
}
