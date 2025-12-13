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

import org.eclipse.apoapsis.ortserver.components.search.apimodel.RunWithPackage
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageCurationDao
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageCurationDataDao
import org.eclipse.apoapsis.ortserver.dao.repositories.resolvedconfiguration.PackageCurationProviderConfigDao
import org.eclipse.apoapsis.ortserver.dao.repositories.resolvedconfiguration.ResolvedConfigurationDao
import org.eclipse.apoapsis.ortserver.dao.repositories.resolvedconfiguration.ResolvedPackageCurationDao
import org.eclipse.apoapsis.ortserver.dao.repositories.resolvedconfiguration.ResolvedPackageCurationProviderDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifierDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.PackageCurationProviderConfig
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Package
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageCurationData

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction

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
        packageId = pkgId.toCoordinates(),
        purl = null
    )
}

/**
 * Create a run with a package and add a PURL curation for it.
 * Returns the RunWithPackage with the curated PURL in the purl field (for PURL search testing).
 */
fun createRunWithCuratedPurl(
    db: Database,
    fixtures: Fixtures,
    repoId: Long,
    pkgId: Identifier,
    curatedPurl: String
): RunWithPackage {
    val pkg = fixtures.generatePackage(pkgId)
    val ortRun = fixtures.createAnalyzerRunWithPackages(packages = setOf(pkg), repositoryId = repoId)

    transaction(db) {
        // Create the curation data with the curated PURL
        val curationData = PackageCurationDataDao.getOrPut(
            PackageCurationData(purl = curatedPurl)
        )

        // Find the identifier DAO for the package
        val identifierDao = IdentifierDao.find {
            (IdentifiersTable.type eq pkgId.type) and
                (IdentifiersTable.namespace eq pkgId.namespace) and
                (IdentifiersTable.name eq pkgId.name) and
                (IdentifiersTable.version eq pkgId.version)
        }.first()

        // Create the package curation
        val packageCuration = PackageCurationDao.new {
            identifier = identifierDao
            packageCurationData = curationData
        }

        // Create the resolved configuration for this ORT run
        val resolvedConfig = ResolvedConfigurationDao.getOrPut(ortRun.id)

        // Create the curation provider config
        val providerConfig = PackageCurationProviderConfigDao.getOrPut(
            PackageCurationProviderConfig(name = "TestProvider")
        )

        // Create the resolved package curation provider
        val resolvedProvider = ResolvedPackageCurationProviderDao.new {
            resolvedConfiguration = resolvedConfig
            packageCurationProviderConfig = providerConfig
            rank = 0
        }

        // Create the resolved package curation
        ResolvedPackageCurationDao.new {
            resolvedPackageCurationProvider = resolvedProvider
            this.packageCuration = packageCuration
            rank = 0
        }
    }

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

fun Package.toPurl(): String = purl
