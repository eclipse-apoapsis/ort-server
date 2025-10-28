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

package org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.entityQuery
import org.eclipse.apoapsis.ortserver.dao.mapAndDeduplicate
import org.eclipse.apoapsis.ortserver.dao.queries.ortrun.GetIssuesForOrtRunQuery
import org.eclipse.apoapsis.ortserver.dao.tables.PackageProvenanceDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.EnvironmentDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.OrtRunIssueDao
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.repositories.ScannerRunRepository
import org.eclipse.apoapsis.ortserver.model.runs.Environment
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.scanner.KnownProvenance
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ProvenanceResolutionResult
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScannerConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScannerRun

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert

/**
 * An implementation of [ScannerRunRepository] that stores scanner runs in the [ScannerRunsTable].
 */
class DaoScannerRunRepository(private val db: Database) : ScannerRunRepository {
    override fun create(scannerJobId: Long): ScannerRun = db.blockingQuery {
        val scannerRunDao = ScannerRunDao.new {
            this.scannerJobId = scannerJobId
        }

        scannerRunDao.mapToModel()
    }

    override fun update(
        id: Long,
        startTime: Instant,
        endTime: Instant,
        environment: Environment,
        config: ScannerConfiguration,
        scanners: Map<Identifier, Set<String>>,
        issues: Map<Identifier, Set<Issue>>
    ) = db.blockingQuery {
        val scannerRunDao = ScannerRunDao[id]

        require(scannerRunDao.startTime == null) {
            "Called 'update' for scanner run '${scannerRunDao.id}' which already has a start time set."
        }

        scannerRunDao.startTime = startTime
        scannerRunDao.endTime = endTime
        scannerRunDao.environment = EnvironmentDao.getOrPut(environment)

        createScannerConfiguration(scannerRunDao, config)
        createScanners(scannerRunDao, scanners)
        createIssues(scannerRunDao, issues)

        scannerRunDao.mapToModel()
    }

    override fun get(id: Long): ScannerRun? = db.entityQuery {
        val scannerRunDao = ScannerRunDao[id]

        // Get the provenance resolution results for the scanned packages.
        val provenanceResolutionResults = getProvenanceResolutionResults(scannerRunDao.packageProvenances)

        // Get the scan results for the scanner run.
        val scanResults = scannerRunDao.scanResults.mapTo(mutableSetOf()) { it.mapToModel() }

        val scanners = mutableMapOf<Identifier, MutableSet<String>>()
        ScannerRunsScannersDao.find { ScannerRunsScannersTable.scannerRunId eq id }.forEach { dao ->
            scanners.getOrPut(dao.identifier.mapToModel()) { mutableSetOf() }.add(dao.scannerName)
        }

        val ortRunId = scannerRunDao.scannerJob.ortRun.id.value
        val scannerIssues = GetIssuesForOrtRunQuery(ortRunId, ScannerRunDao.ISSUE_WORKER_TYPE).execute()
        val issuesById = mutableMapOf<Identifier, MutableSet<Issue>>()
        scannerIssues.forEach { issue ->
            issue.identifier?.also { identifier ->
                issuesById.getOrPut(identifier) { mutableSetOf() } += issue
            }
        }

        scannerRunDao.mapToModel().copy(
            provenances = provenanceResolutionResults,
            scanResults = scanResults,
            issues = issuesById,
            scanners = scanners
        )
    }

    override fun getByJobId(scannerJobId: Long): ScannerRun? = db.blockingQuery {
        ScannerRunDao.find { ScannerRunsTable.scannerJobId eq scannerJobId }.firstOrNull()?.let { get(it.id.value) }
    }

    /**
     * Get the [ProvenanceResolutionResult]s for the provided [packageProvenances].
     */
    internal fun getProvenanceResolutionResults(
        packageProvenances: Iterable<PackageProvenanceDao>
    ): Set<ProvenanceResolutionResult> = buildSet {
        packageProvenances.groupBy { it.identifier.mapToModel() }.forEach { (identifier, provenanceDaos) ->
            val mappedProvenances = provenanceDaos.map { it to it.mapToModel() }
            val successfulResult = mappedProvenances.find { (_, provenance) -> provenance is KnownProvenance }

            val result = if (successfulResult != null) {
                // If there was a successful provenance resolution for this identifier, use it and ignore any failed
                // package provenance resolutions. This handles the case where resolving the first source code origin
                // failed, but a later attempt for the other source code origin succeeded.

                val packageProvenanceDao = successfulResult.first
                val packageProvenance = successfulResult.second as KnownProvenance
                val nestedProvenance = packageProvenanceDao.nestedProvenance

                if (packageProvenanceDao.vcs != null && nestedProvenance == null) {
                    // If the provenance is a repository provenance but no nested provenance was stored, this means that
                    // the nested provenance resolution failed, so create an error in this case.
                    ProvenanceResolutionResult(
                        id = identifier,
                        packageProvenance = packageProvenance,
                        nestedProvenanceResolutionIssue = Issue(
                            timestamp = Clock.System.now(),
                            source = "Scanner",
                            message = "Could not resolve nested provenance for provenance '$packageProvenance'.",
                            severity = Severity.ERROR
                        )
                    )
                } else {
                    ProvenanceResolutionResult(
                        id = identifier,
                        packageProvenance = packageProvenance,
                        subRepositories = nestedProvenance?.subRepositories?.associate {
                            it.path to it.vcs.mapToModel()
                        }.orEmpty()
                    )
                }
            } else {
                // If there was no successful provenance resolution for this identifier, collect the errors from all
                // failed attempts.
                val errors = mappedProvenances.mapNotNull { (packageProvenanceDao, _) ->
                    packageProvenanceDao.errorMessage
                }

                ProvenanceResolutionResult(
                    id = identifier,
                    packageProvenanceResolutionIssue = Issue(
                        timestamp = Clock.System.now(),
                        source = "Scanner",
                        message = "Could not resolve provenance for package '$identifier':\n" +
                                errors.joinToString("\n"),
                        severity = Severity.ERROR
                    )
                )
            }

            add(result)
        }
    }
}

private fun createScannerConfiguration(
    scannerRunDao: ScannerRunDao,
    scannerConfiguration: ScannerConfiguration
): ScannerConfigurationDao {
    val detectedLicenseMappings = mapAndDeduplicate(scannerConfiguration.detectedLicenseMappings.entries) {
        DetectedLicenseMappingDao.getOrPut(it.toPair())
    }

    val scannerConfigurationDao = ScannerConfigurationDao.new {
        this.scannerRun = scannerRunDao
        this.skipConcluded = scannerConfiguration.skipConcluded
        this.skipExcluded = scannerConfiguration.skipExcluded
        this.ignorePatterns = scannerConfiguration.ignorePatterns
        this.detectedLicenseMappings = detectedLicenseMappings
    }

    scannerConfiguration.config.forEach { (scanner, pluginConfig) ->
        pluginConfig.options.forEach { (option, value) ->
            val optionDao = ScannerConfigurationOptionDao.getOrPut(scanner, option, value)

            ScannerConfigurationsOptionsTable.insert {
                it[scannerConfigurationId] = scannerConfigurationDao.id
                it[scannerConfigurationOptionId] = optionDao.id
            }
        }

        pluginConfig.secrets.forEach { (secret, value) ->
            val secretDao = ScannerConfigurationSecretDao.getOrPut(scanner, secret, value)

            ScannerConfigurationsSecretsTable.insert {
                it[scannerConfigurationId] = scannerConfigurationDao.id
                it[scannerConfigurationSecretId] = secretDao.id
            }
        }
    }

    return scannerConfigurationDao
}

private fun createScanners(run: ScannerRunDao, scanners: Map<Identifier, Set<String>>) {
    scanners.entries.forEach { (id, scannerNames) ->
        scannerNames.forEach { scanner ->
            ScannerRunsScannersDao.addScanner(run, id, scanner)
        }
    }
}

private fun createIssues(run: ScannerRunDao, issuesById: Map<Identifier, Set<Issue>>) {
    val ortRunId = run.scannerJob.ortRun.id.value
    issuesById.entries.forEach { (identifier, issues) ->
        issues.forEach { issue ->
            OrtRunIssueDao.createByIssue(
                ortRunId,
                issue.copy(identifier = identifier, worker = ScannerRunDao.ISSUE_WORKER_TYPE)
            )
        }
    }
}
