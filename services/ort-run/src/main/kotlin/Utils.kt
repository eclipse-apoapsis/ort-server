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

package org.eclipse.apoapsis.ortserver.services.ortrun

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.AnalyzerJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesAnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageCurationDataTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageCurationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.resolvedconfiguration.ResolvedConfigurationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.resolvedconfiguration.ResolvedPackageCurationProvidersTable
import org.eclipse.apoapsis.ortserver.dao.repositories.resolvedconfiguration.ResolvedPackageCurationsTable
import org.eclipse.apoapsis.ortserver.utils.logging.runBlocking

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.jdbc.select

import org.ossreviewtoolkit.model.FileList
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.scanner.utils.FileListResolver

/**
 * Use the [fileListResolver] to get the [FileList]s for the provided [provenances]. If a [FileList] is not
 * available for a provenance, it is ignored and not included in the result.
 */
internal fun getFileLists(fileListResolver: FileListResolver, provenances: Set<KnownProvenance>) =
    runBlocking(Dispatchers.IO.limitedParallelism(20)) {
        provenances.map { provenance ->
            async {
                fileListResolver.get(provenance)?.let { fileList ->
                    FileList(
                        provenance,
                        fileList.files.mapTo(mutableSetOf()) { FileList.Entry(it.path, it.sha1) }
                    )
                }
            }
        }.awaitAll().filterNotNull()
    }

/**
 * Return package purls by identifier ID for the given [ortRunId]. Curated purls override base purls.
 */
internal fun getPurlByIdentifierIdForOrtRun(ortRunId: Long, identifierIds: Collection<Long>): Map<Long, String> {
    if (identifierIds.isEmpty()) return emptyMap()

    val basePurls = PackagesTable
        .innerJoin(PackagesAnalyzerRunsTable)
        .innerJoin(AnalyzerRunsTable)
        .innerJoin(AnalyzerJobsTable)
        .select(PackagesTable.identifierId, PackagesTable.purl)
        .where {
            (AnalyzerJobsTable.ortRunId eq ortRunId) and
                (PackagesTable.identifierId inList identifierIds.toList())
        }
        .associate { it[PackagesTable.identifierId].value to it[PackagesTable.purl] }

    // ORDER BY rank ensures highest-priority curation comes first; groupBy preserves this order.
    val curatedPurls = PackageCurationDataTable
        .innerJoin(PackageCurationsTable)
        .innerJoin(ResolvedPackageCurationsTable)
        .innerJoin(ResolvedPackageCurationProvidersTable)
        .innerJoin(ResolvedConfigurationsTable)
        .select(PackageCurationsTable.identifierId, PackageCurationDataTable.purl)
        .where {
            (ResolvedConfigurationsTable.ortRunId eq ortRunId) and
                (PackageCurationsTable.identifierId inList identifierIds.toList()) and
                (PackageCurationDataTable.purl.isNotNull())
        }
        .orderBy(ResolvedPackageCurationProvidersTable.rank)
        .orderBy(ResolvedPackageCurationsTable.rank)
        .groupBy { it[PackageCurationsTable.identifierId].value }
        .mapValues { (_, rows) ->
            requireNotNull(rows.first()[PackageCurationDataTable.purl]) {
                "Curated purl was unexpectedly null after filtering."
            }
        }

    return basePurls + curatedPurls
}
