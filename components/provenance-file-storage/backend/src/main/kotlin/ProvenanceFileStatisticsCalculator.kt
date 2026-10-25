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

package org.eclipse.apoapsis.ortserver.components.provenancefilestorage

import kotlin.time.Instant

/** The maximum number of URLs to include in [ProvenanceFileStatistics.topUrls]. */
internal const val TOP_URLS_COUNT = 10

/** Compute [ProvenanceFileStatistics] for the given [files] that were listed from the storage at [snapshotTime]. */
internal fun computeStatistics(files: List<ProvenanceFile>, snapshotTime: Instant): ProvenanceFileStatistics {
    val totalSize = files.sumOf { it.sizeInBytes }
    val lastModifiedTimes = files.mapNotNull { it.lastModified }

    return ProvenanceFileStatistics(
        snapshotTime = snapshotTime,
        totalCount = files.size.toLong(),
        totalSizeInBytes = totalSize,
        averageSizeInBytes = if (files.isEmpty()) 0 else totalSize / files.size,
        largest = files.maxByOrNull { it.sizeInBytes },
        oldestLastModified = lastModifiedTimes.minOrNull(),
        newestLastModified = lastModifiedTimes.maxOrNull(),
        byKind = files.groupBy { it.kind }.map { (kind, filesOfKind) ->
            ProvenanceFileKindStatistics(kind, filesOfKind.size.toLong(), filesOfKind.sumOf { it.sizeInBytes })
        }.sortedBy { it.kind },
        topUrls = files.filter { it.url != null }.groupBy { checkNotNull(it.url) }.map { (url, filesForUrl) ->
            ProvenanceFileUrlStatistics(url, filesForUrl.size.toLong(), filesForUrl.sumOf { it.sizeInBytes })
        }.sortedWith(compareByDescending<ProvenanceFileUrlStatistics> { it.sizeInBytes }.thenBy { it.url })
            .take(TOP_URLS_COUNT)
    )
}
