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

package org.eclipse.apoapsis.ortserver.services.ortrun

import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.AnalyzerJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesAnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageCurationDataTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageCurationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.resolvedconfiguration.ResolvedConfigurationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.resolvedconfiguration.ResolvedPackageCurationProvidersTable
import org.eclipse.apoapsis.ortserver.dao.repositories.resolvedconfiguration.ResolvedPackageCurationsTable

import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.ExpressionWithColumnTypeAlias
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.QueryAlias
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.min
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.core.times
import org.jetbrains.exposed.v1.jdbc.select

internal class CuratedPurlResult(
    val alias: QueryAlias,
    val ortRunId: ExpressionWithColumnTypeAlias<EntityID<Long>>,
    val identifierId: ExpressionWithColumnTypeAlias<EntityID<Long>>,
    val purl: ExpressionWithColumnTypeAlias<String?>
)

internal class PurlResult(
    val alias: QueryAlias,
    val ortRunId: ExpressionWithColumnTypeAlias<EntityID<Long>>,
    val identifierId: ExpressionWithColumnTypeAlias<EntityID<Long>>,
    val purl: ExpressionWithColumnTypeAlias<String>
)

/**
 * Build a `purl_by_run_identifier` relation keyed by (ort_run_id, identifier_id).
 * PURL = COALESCE(curated_purl, base_purl, '').
 */
internal fun buildPurlByRunIdentifier(
    runIdPairs: QueryAlias,
    riOrtRunIdCol: ExpressionWithColumnType<EntityID<Long>>,
    riIdentifierIdCol: ExpressionWithColumnType<EntityID<Long>>
): PurlResult {
    // Base (uncurated) PURL from analyzer packages.
    val bpOrtRunId = riOrtRunIdCol.alias("bp_ort_run_id")
    val bpIdentifierId = riIdentifierIdCol.alias("bp_identifier_id")
    val basePurlMin = PackagesTable.purl.min().alias("base_purl")

    val basePurl = AnalyzerJobsTable
        .innerJoin(AnalyzerRunsTable)
        .innerJoin(PackagesAnalyzerRunsTable)
        .innerJoin(PackagesTable)
        .join(runIdPairs, JoinType.INNER) {
            (AnalyzerJobsTable.ortRunId eq riOrtRunIdCol) and
                (PackagesTable.identifierId eq riIdentifierIdCol)
        }
        .select(bpOrtRunId, bpIdentifierId, basePurlMin)
        .groupBy(bpOrtRunId, bpIdentifierId)
        .alias("base_purl_by_run_identifier")

    // Curated PURL using composite rank (provider_rank * 10000 + curation_rank).
    val curated = buildCuratedPurlByRunIdentifier(runIdPairs, riOrtRunIdCol, riIdentifierIdCol)

    // Combine: COALESCE(curated, base, '').
    val purl = CustomFunction(
        "COALESCE",
        TextColumnType(),
        curated.alias[curated.purl],
        basePurl[basePurlMin],
        stringLiteral("")
    ).alias("purl")
    val outOrtRunId = riOrtRunIdCol.alias("purl_ort_run_id")
    val outIdentifierId = riIdentifierIdCol.alias("purl_identifier_id")

    val query = runIdPairs
        .join(basePurl, JoinType.LEFT) {
            (riOrtRunIdCol eq basePurl[bpOrtRunId]) and
                (riIdentifierIdCol eq basePurl[bpIdentifierId])
        }
        .join(curated.alias, JoinType.LEFT) {
            (riOrtRunIdCol eq curated.alias[curated.ortRunId]) and
                (riIdentifierIdCol eq curated.alias[curated.identifierId])
        }
        .select(outOrtRunId, outIdentifierId, purl)
        .alias("purl_by_run_identifier")

    return PurlResult(query, outOrtRunId, outIdentifierId, purl)
}

/**
 * Find the highest-priority curated PURL for each (ort_run_id, identifier_id) pair.
 *
 * Curations are prioritized by two independent rank dimensions: the *provider rank*
 * (which curation provider supplied it) and the *curation rank* (ordering within that provider).
 * The correct priority is lexicographic: lowest provider_rank wins first, then lowest
 * curation_rank breaks ties within the same provider.
 *
 * Replace two separate min/filter passes (find min provider_rank, filter, then find
 * min curation_rank within those, filter again) with collapsing both dimensions into a
 * single composite key which preserves the lexicographic ordering (provider_rank is
 * always the dominant term).
 *
 * With the composite rank, the resolution becomes 3 stages instead of 5:
 * 1. Collect all candidates with their composite rank
 * 2. Find the minimum composite rank per (ort_run_id, identifier_id)
 * 3. Join back once to retrieve the PURL at that rank (with `min()` as a tiebreaker
 *    for the theoretical case of duplicate composite ranks)
 *
 * As a side benefit, fewer subquery stages means fewer plan nodes for PostgreSQL's JIT
 * compiler to process, modestly reducing compilation overhead.
 */
internal fun buildCuratedPurlByRunIdentifier(
    runIdPairs: QueryAlias,
    riOrtRunIdCol: ExpressionWithColumnType<EntityID<Long>>,
    riIdentifierIdCol: ExpressionWithColumnType<EntityID<Long>>
): CuratedPurlResult {
    // Stage 1: All curated PURL candidates with composite rank.
    val ccOrtRunId = riOrtRunIdCol.alias("cc_ort_run_id")
    val ccIdentifierId = riIdentifierIdCol.alias("cc_identifier_id")
    val compositeRank =
        (ResolvedPackageCurationProvidersTable.rank * 10000 + ResolvedPackageCurationsTable.rank)
            .alias("composite_rank")
    val ccPurl = PackageCurationDataTable.purl.alias("cc_purl")

    val candidates = ResolvedConfigurationsTable
        .innerJoin(ResolvedPackageCurationProvidersTable)
        .innerJoin(ResolvedPackageCurationsTable)
        .innerJoin(PackageCurationsTable)
        .innerJoin(PackageCurationDataTable)
        .join(runIdPairs, JoinType.INNER) {
            (ResolvedConfigurationsTable.ortRunId eq riOrtRunIdCol) and
                (PackageCurationsTable.identifierId eq riIdentifierIdCol)
        }
        .select(ccOrtRunId, ccIdentifierId, compositeRank, ccPurl)
        .where { PackageCurationDataTable.purl.isNotNull() }
        .alias("curated_candidates")

    // Stage 2: Min composite rank per (ort_run_id, identifier_id).
    val mrOrtRunId = candidates[ccOrtRunId].alias("mr_ort_run_id")
    val mrIdentifierId = candidates[ccIdentifierId].alias("mr_identifier_id")
    val minRank = candidates[compositeRank].min().alias("min_composite_rank")

    val minRanks = candidates
        .select(mrOrtRunId, mrIdentifierId, minRank)
        .groupBy(mrOrtRunId, mrIdentifierId)
        .alias("curated_min_rank")

    // Stage 3: Join back to get PURL at min rank, with min() as tiebreaker.
    val outOrtRunId = candidates[ccOrtRunId].alias("cp_ort_run_id")
    val outIdentifierId = candidates[ccIdentifierId].alias("cp_identifier_id")
    val curatedPurl = candidates[ccPurl].min().alias("curated_purl")

    val query = candidates
        .join(minRanks, JoinType.INNER) {
            (candidates[ccOrtRunId] eq minRanks[mrOrtRunId]) and
                (candidates[ccIdentifierId] eq minRanks[mrIdentifierId]) and
                (candidates[compositeRank] eq minRanks[minRank])
        }
        .select(outOrtRunId, outIdentifierId, curatedPurl)
        .groupBy(outOrtRunId, outIdentifierId)
        .alias("curated_purl_by_run_identifier")

    return CuratedPurlResult(query, outOrtRunId, outIdentifierId, curatedPurl)
}
