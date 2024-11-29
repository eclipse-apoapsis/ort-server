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

import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.scanner.CopyrightFinding
import org.eclipse.apoapsis.ortserver.model.runs.scanner.LicenseFinding
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScanSummary
import org.eclipse.apoapsis.ortserver.model.runs.scanner.Snippet
import org.eclipse.apoapsis.ortserver.model.runs.scanner.SnippetFinding
import org.eclipse.apoapsis.ortserver.model.runs.scanner.TextLocation

import org.ossreviewtoolkit.model.CopyrightFinding as OrtCopyrightFinding
import org.ossreviewtoolkit.model.Issue as OrtIssue
import org.ossreviewtoolkit.model.LicenseFinding as OrtLicenseFinding
import org.ossreviewtoolkit.model.ScanSummary as OrtScanSummary
import org.ossreviewtoolkit.model.Snippet as OrtSnippet
import org.ossreviewtoolkit.model.SnippetFinding as OrtSnippetFinding
import org.ossreviewtoolkit.model.TextLocation as OrtTextLocation

/**
 * Compare the given [ortSummary] with the given [serverSummary] and return a flag with the result.
 */
internal fun compareScanSummaries(ortSummary: OrtScanSummary, serverSummary: ScanSummary): Boolean {
    @Suppress("ComplexCondition")
    if (ortSummary.licenseFindings.size != serverSummary.licenseFindings.size ||
        ortSummary.copyrightFindings.size != serverSummary.copyrightFindings.size ||
        ortSummary.snippetFindings.size != serverSummary.snippetFindings.size ||
        ortSummary.issues.size != serverSummary.issues.size
    ) {
        return false
    }

    return compareCollections(ortSummary.licenseFindings, serverSummary.licenseFindings, ::compareLicenseFinding) &&
            compareCollections(
                ortSummary.copyrightFindings,
                serverSummary.copyrightFindings,
                ::compareCopyrightFinding
            ) &&
            compareCollections(ortSummary.snippetFindings, serverSummary.snippetFindings, ::compareSnippetFinding) &&
            compareCollections(ortSummary.issues, serverSummary.issues, ::compareIssues)
}

/**
 * Compare the given collections [col1] and [col2] using the given [compare] function and return a flag with the result.
 */
private fun <S, T> compareCollections(col1: Collection<S>, col2: Collection<T>, compare: (S, T) -> Boolean): Boolean =
    col1.zip(col2).all { (item1, item2) -> compare(item1, item2) }

/**
 * Compare the given [ortLicenseFinding] with the given [serverLicenseFinding] and return a flag with the result.
 */
private fun compareLicenseFinding(ortLicenseFinding: OrtLicenseFinding, serverLicenseFinding: LicenseFinding): Boolean =
    ortLicenseFinding.license.toString() == serverLicenseFinding.spdxLicense &&
            compareTextLocations(ortLicenseFinding.location, serverLicenseFinding.location)

/**
 * Compare the given [ortCopyrightFinding] with the given [serverCopyrightFinding] and return a flag with the result.
 */
private fun compareCopyrightFinding(
    ortCopyrightFinding: OrtCopyrightFinding,
    serverCopyrightFinding: CopyrightFinding
): Boolean =
    ortCopyrightFinding.statement == serverCopyrightFinding.statement &&
            compareTextLocations(ortCopyrightFinding.location, serverCopyrightFinding.location)

/**
 * Compare the given [ortSnippetFinding] with the given [serverSnippetFinding] and return a flag with the result.
 */
private fun compareSnippetFinding(
    ortSnippetFinding: OrtSnippetFinding,
    serverSnippetFinding: SnippetFinding
): Boolean {
    if (ortSnippetFinding.snippets.size != serverSnippetFinding.snippets.size) return false

    return compareTextLocations(ortSnippetFinding.sourceLocation, serverSnippetFinding.location) &&
            compareCollections(ortSnippetFinding.snippets, serverSnippetFinding.snippets, ::compareSnippets)
}

/**
 * Compare the given [ortSnippet] with the given [serverSnippet] and return a flag with the result.
 */
private fun compareSnippets(ortSnippet: OrtSnippet, serverSnippet: Snippet): Boolean =
    ortSnippet.purl == serverSnippet.purl &&
            ortSnippet.license.toString() == serverSnippet.spdxLicense &&
            compareTextLocations(ortSnippet.location, serverSnippet.location)

/**
 * Compare the given [ortIssue] with the given [serverIssue] and return a flag with the result.
 */
private fun compareIssues(ortIssue: OrtIssue, serverIssue: Issue): Boolean =
    ortIssue.source == serverIssue.source &&
            ortIssue.message == serverIssue.message &&
            ortIssue.severity.name == serverIssue.severity.name

/**
 * Compare the given [ortLocation] with the given [serverLocation] and return a flag with the result.
 */
private fun compareTextLocations(ortLocation: OrtTextLocation, serverLocation: TextLocation): Boolean =
    ortLocation.startLine == serverLocation.startLine &&
            ortLocation.endLine == serverLocation.endLine &&
            ortLocation.path == serverLocation.path
