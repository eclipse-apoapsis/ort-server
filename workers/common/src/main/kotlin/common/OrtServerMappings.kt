/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.common

import org.ossreviewtoolkit.model.Hash as OrtHash
import org.ossreviewtoolkit.model.HashAlgorithm.Companion as OrtHashAlgorithm
import org.ossreviewtoolkit.model.Identifier as OrtIdentifier
import org.ossreviewtoolkit.model.Package as OrtPackage
import org.ossreviewtoolkit.model.RemoteArtifact as OrtRemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo as OrtVcsInfo
import org.ossreviewtoolkit.model.VcsType as OrtVcsType
import org.ossreviewtoolkit.server.model.runs.Identifier
import org.ossreviewtoolkit.server.model.runs.Package
import org.ossreviewtoolkit.server.model.runs.RemoteArtifact
import org.ossreviewtoolkit.server.model.runs.VcsInfo

fun Package.mapToOrt() =
    OrtPackage(
        id = identifier.mapToOrt(),
        purl = purl,
        cpe = cpe,
        authors = authors,
        declaredLicenses = declaredLicenses,
        description = description,
        homepageUrl = homepageUrl,
        binaryArtifact = binaryArtifact.mapToOrt(),
        sourceArtifact = sourceArtifact.mapToOrt(),
        vcs = vcs.mapToOrt(),
        vcsProcessed = vcsProcessed.mapToOrt(),
        isMetadataOnly = isMetadataOnly,
        isModified = isModified
    )

fun Identifier.mapToOrt() = OrtIdentifier(type, namespace, name, version)

fun RemoteArtifact.mapToOrt() =
    OrtRemoteArtifact(
        url = url,
        hash = OrtHash(
            value = hashValue,
            algorithm = OrtHashAlgorithm.fromString(hashAlgorithm)
        )
    )

fun VcsInfo.mapToOrt() = OrtVcsInfo(OrtVcsType(type.name), url, revision, path)
