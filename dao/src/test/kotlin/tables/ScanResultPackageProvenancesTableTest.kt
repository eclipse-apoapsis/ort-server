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

package org.eclipse.apoapsis.ortserver.dao.tables

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import kotlin.time.Clock

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifierDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.RemoteArtifactDao
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.RemoteArtifact

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

class ScanResultPackageProvenancesTableTest : WordSpec() {
    private val dbExtension = extension(DatabaseTestExtension())

    init {
        "insertIfNotExists" should {
            "create a row when none exists" {
                dbExtension.db.blockingQuery {
                    val scanResult = createScanResult()
                    val provenance = createPackageProvenance()

                    ScanResultPackageProvenancesTable.insertIfNotExists(
                        scanResultId = scanResult.id.value,
                        packageProvenanceId = provenance.id.value
                    )

                    val count = ScanResultPackageProvenancesTable.selectAll().where {
                        (ScanResultPackageProvenancesTable.scanResultId eq scanResult.id) and
                                (ScanResultPackageProvenancesTable.packageProvenanceId eq provenance.id)
                    }.count()

                    count shouldBe 1L
                }
            }

            "be idempotent on repeated calls" {
                dbExtension.db.blockingQuery {
                    val scanResult = createScanResult()
                    val provenance = createPackageProvenance()

                    repeat(3) {
                        ScanResultPackageProvenancesTable.insertIfNotExists(
                            scanResultId = scanResult.id.value,
                            packageProvenanceId = provenance.id.value
                        )
                    }

                    val count = ScanResultPackageProvenancesTable.selectAll().where {
                        (ScanResultPackageProvenancesTable.scanResultId eq scanResult.id) and
                                (ScanResultPackageProvenancesTable.packageProvenanceId eq provenance.id)
                    }.count()

                    count shouldBe 1L
                }
            }

            "raise a FK violation for a non-existent scan_result_id" {
                dbExtension.db.blockingQuery {
                    val provenance = createPackageProvenance()

                    shouldThrow<Exception> {
                        ScanResultPackageProvenancesTable.insertIfNotExists(
                            scanResultId = Long.MAX_VALUE,
                            packageProvenanceId = provenance.id.value
                        )
                    }
                }
            }
        }
    }

    private fun createScanResult(): ScanResultDao =
        dbExtension.db.blockingQuery {
            val scanSummary = ScanSummaryDao.new {
                startTime = Clock.System.now()
                endTime = Clock.System.now()
                hash = Clock.System.now().toString()
            }

            ScanResultDao.new {
                this.scanSummary = scanSummary
                this.scannerName = "test-scanner"
                this.scannerVersion = "1.0.0"
                this.scannerConfiguration = ""
                this.artifactUrl = "https://example.com/artifact.zip"
                this.artifactHash = "abc123"
                this.artifactHashAlgorithm = "SHA-1"
            }
        }

    private fun createPackageProvenance(): PackageProvenanceDao =
        dbExtension.db.blockingQuery {
            val identifier = IdentifierDao.getOrPut(
                Identifier(type = "Maven", namespace = "com.example", name = "lib", version = "1.0")
            )
            val artifact = RemoteArtifactDao.getOrPut(
                RemoteArtifact(url = "https://example.com/artifact.zip", hashValue = "abc123", hashAlgorithm = "SHA-1")
            )

            PackageProvenanceDao.new {
                this.identifier = identifier
                this.artifact = artifact
            }
        }
}
