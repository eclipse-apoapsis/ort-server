/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import java.security.MessageDigest

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.Snippet
import org.ossreviewtoolkit.model.SnippetFinding
import org.ossreviewtoolkit.model.TextLocation

/** The algorithm to use for hashing. */
private const val HASH_ALGORITHM = "SHA-256"

/**
 * Calculate a hash value for the given [ortSummary]. This value is then stored together with the summary to allow
 * finding duplicates efficiently.
 */
@OptIn(ExperimentalStdlibApi::class)
internal fun calculateScanSummaryHash(ortSummary: ScanSummary): String {
    val digest = MessageDigest.getInstance(HASH_ALGORITHM)

    digest.updateString("${ortSummary.startTime},${ortSummary.endTime}")

    ortSummary.licenseFindings.forEach { it.hash(digest) }
    ortSummary.copyrightFindings.forEach { it.hash(digest) }
    ortSummary.snippetFindings.forEach { it.hash(digest) }
    ortSummary.issues.forEach { it.hash(digest) }

    return digest.digest().toHexString()
}

/**
 * Update the given [digest] to calculate a hash value for this [LicenseFinding].
 */
private fun LicenseFinding.hash(digest: MessageDigest) {
    digest.updateString(license.toString())
    location.hash(digest)
}

/**
 * Update the given [digest] to calculate a hash value for this [CopyrightFinding].
 */
private fun CopyrightFinding.hash(digest: MessageDigest) {
    digest.updateString(statement)
    location.hash(digest)
}

/**
 * Update the given [digest] to calculate a hash value for this [SnippetFinding].
 */
private fun SnippetFinding.hash(digest: MessageDigest) {
    sourceLocation.hash(digest)
    snippets.forEach { it.hash(digest) }
}

/**
 * Update the given [digest] to calculate a hash value for this [Snippet].
 */
private fun Snippet.hash(digest: MessageDigest) {
    digest.updateString("$purl,$license,$location")
}

/**
 * Update the given [digest] to calculate a hash value for this [Issue].
 */
private fun Issue.hash(digest: MessageDigest) {
    digest.updateString("$message,$severity,$source")
}

/**
 * Update the given [digest] to calculate a hash value for this [TextLocation].
 */
private fun TextLocation.hash(digest: MessageDigest) {
    digest.updateString("$startLine,$endLine,$path")
}

/**
 * Update this [MessageDigest] with the given [str].
 */
private fun MessageDigest.updateString(str: String) {
    update(str.toByteArray())
}
