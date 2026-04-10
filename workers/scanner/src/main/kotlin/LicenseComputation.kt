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

package org.eclipse.apoapsis.ortserver.workers.scanner

import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackageDao
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectDao
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProjectsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifierDao
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToModel

import org.jetbrains.exposed.v1.core.eq

import org.ossreviewtoolkit.model.Identifier as OrtIdentifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.licenses.LicenseView

/**
 * Compute detected licenses for a package or project by aggregating license findings across all scan results
 * for the given [id]. Returns the union of all license strings found in all provenances.
 */
fun computeDetectedLicenses(
    scannerRun: ScannerRun,
    id: OrtIdentifier
): Set<String> =
    scannerRun.getAllScanResults()[id]
        .orEmpty()
        .flatMap { scanResult -> scanResult.summary.licenseFindings }
        .map { licenseFinding -> licenseFinding.license.toString() }
        .toSet()

/**
 * Compute the effective license for a package or project using the [licenseInfoResolver].
 *
 * The effective license follows the precedence: concluded → declared → detected (via LicenseView).
 * Returns null if none of these are available.
 */
fun computeEffectiveLicense(
    licenseInfoResolver: LicenseInfoResolver,
    id: OrtIdentifier
): String? {
    val resolvedLicenseInfo = licenseInfoResolver.resolveLicenseInfo(id)

    return resolvedLicenseInfo.effectiveLicense(LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED)?.toString()
}

/**
 * Compute detected and effective licenses for all packages and projects in the [ortResult] and store them in the
 * database. Uses the [scannerRun] to aggregate detected licenses across all provenances and the
 * [licenseInfoResolver] to compute effective licenses.
 */
fun computeAndStoreLicenses(
    ortResult: OrtResult,
    scannerRun: ScannerRun,
    licenseInfoResolver: LicenseInfoResolver
) {
    for (curatedPackage in ortResult.getPackages()) {
        val id = curatedPackage.metadata.id
        val modelId = id.mapToModel()

        val detectedLicenses = computeDetectedLicenses(scannerRun, id)
        val effectiveLicense = computeEffectiveLicense(licenseInfoResolver, id)

        if (detectedLicenses.isNotEmpty() || effectiveLicense != null) {
            updatePackageLicenses(modelId, detectedLicenses, effectiveLicense)
        }
    }

    for (project in ortResult.getProjects()) {
        val id = project.id
        val modelId = id.mapToModel()

        val detectedLicenses = computeDetectedLicenses(scannerRun, id)
        val effectiveLicense = computeEffectiveLicense(licenseInfoResolver, id)

        if (detectedLicenses.isNotEmpty() || effectiveLicense != null) {
            updateProjectLicenses(modelId, detectedLicenses, effectiveLicense)
        }
    }
}

/**
 * Update the license fields for the package identified by [modelId] in the database.
 */
fun updatePackageLicenses(
    modelId: Identifier,
    detectedLicenses: Set<String>,
    effectiveLicense: String?
) {
    val identifierDao = IdentifierDao.findByIdentifier(modelId)
        ?: return

    val packageDao = PackageDao.find {
        PackagesTable.identifierId eq identifierDao.id
    }.firstOrNull() ?: return

    packageDao.detectedLicenses = detectedLicenses.joinToString(",").takeIf { it.isNotEmpty() }
    packageDao.effectiveLicense = effectiveLicense
}

/**
 * Update the license fields for the project identified by [modelId] in the database.
 */
fun updateProjectLicenses(
    modelId: Identifier,
    detectedLicenses: Set<String>,
    effectiveLicense: String?
) {
    val identifierDao = IdentifierDao.findByIdentifier(modelId)
        ?: return

    val projectDao = ProjectDao.find {
        ProjectsTable.identifierId eq identifierDao.id
    }.firstOrNull() ?: return

    projectDao.detectedLicenses = detectedLicenses.joinToString(",").takeIf { it.isNotEmpty() }
    projectDao.effectiveLicense = effectiveLicense
}
