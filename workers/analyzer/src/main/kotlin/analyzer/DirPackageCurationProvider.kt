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

package org.eclipse.apoapsis.ortserver.workers.analyzer

import java.io.File

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProvider
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProviderFactory
import org.ossreviewtoolkit.utils.common.encodeOr
import org.ossreviewtoolkit.utils.ort.runBlocking

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(DirPackageCurationProvider::class.java)

/**
 * A specialized [PackageCurationProvider] implementation that reads curation data from a folder structure that
 * adheres to specific conventions.
 *
 * The expected folder structure is based on the components of an identifier. On top level, a directory corresponding
 * to the package type must be present. On the next level, the provider searches for a subfolder whose name is equal to
 * the namespace (or an underscore character if the namespace is undefined). In this subfolder, a file is expected
 * whose name matches the identifier's _name_ component with the extension `.yml`. This file can contain package
 * curations for the different versions of this package. So, for instance, curations for the package
 * `Maven:org.apache.commons:commons-lang3:3.12` would be looked up in a file named `commons-lang3.yml` under the
 * relative path `Maven/org.apache.commons`.
 *
 * There are some corner cases with identifiers that cannot be mapped directly to paths on a file system. These are
 * handled as follows:
 * - An empty component of the identifier (e.g., a missing namespace) is mapped to the underscore character ("_").
 * - Some special characters that are problematic in file names are mapped to their numeric hexadecimal representation
 *   (%XX).
 */
@OrtPlugin(
    displayName = "Dir Package Curation Provider",
    description = "A package curation provider that reads curation data from a folder structure.",
    factory = PackageCurationProviderFactory::class
)
class DirPackageCurationProvider(
    override val descriptor: PluginDescriptor,
    config: DirPackageCurationProviderConfig
) : PackageCurationProvider {
    /** The root directory of the folder structure with curation files. */
    private val root = File(config.path)

    override fun getCurationsFor(packages: Collection<Package>): Set<PackageCuration> =
        runBlocking(Dispatchers.IO) {
            packages.map { pkg ->
                async { lookupCurations(pkg) }
            }.awaitAll().flatten().toSet()
        }

    /**
     * Lookup the curations file for the given [pkg]. If it exists, load it and return the applicable curations.
     */
    private fun lookupCurations(pkg: Package): List<PackageCuration> {
        val curationFile = root.resolve(pkg.id.toCurationPath())
        logger.debug("Looking up curation file '{}'.", curationFile.absolutePath)

        return curationFile.takeIf { it.isFile }?.readValue<List<PackageCuration>>().orEmpty().filter {
            it.isApplicable(pkg.id)
        }
    }
}

/**
 * A data class defining the configuration options supported by [DirPackageCurationProvider].
 */
data class DirPackageCurationProviderConfig(
    /** The root path of the folder structure to be searched by the provider. */
    val path: String
)

/**
 * The path must be aligned with the
 * [conventions for the ort-config repository](https://github.com/oss-review-toolkit/ort-config#curations).
 */
private fun Identifier.toCurationPath() = "${type.encodeOr("_")}/${namespace.encodeOr("_")}/${name.encodeOr("_")}.yml"
