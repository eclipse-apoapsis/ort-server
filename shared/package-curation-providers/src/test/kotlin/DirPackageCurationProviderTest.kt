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

package org.eclipse.apoapsis.ortserver.shared.packagecurationproviders

import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.should

import java.io.File

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.PackageCurationData
import org.ossreviewtoolkit.model.config.ProviderPluginConfiguration
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.model.writeValue
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProvider
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProviderFactory

class DirPackageCurationProviderTest : StringSpec({
    "An empty set should be returned if no package curations are found" {
        val root = tempdir()
        val provider = createProvider(root)

        val curations = provider.getCurationsFor(listOf(packageLang, packageText))

        curations should beEmpty()
    }

    "The correct package curations should be returned" {
        val root = tempdir()
        val curationFile = root.resolve(PACKAGE_TYPE).resolve(NAMESPACE).resolve("$NAME.yml")
        val curation = createCuration(packageLang.id)
        curationFile.writeValue(listOf(curation, createCuration(packageLang.id.copy(version = "1.0"))))

        val provider = createProvider(root)

        val curations = provider.getCurationsFor(listOf(packageText, packageLang))

        curations shouldContainExactlyInAnyOrder listOf(curation)
    }

    "Special characters in identifiers should be encoded" {
        val root = tempdir()
        val packageAngular = Package.EMPTY.copy(id = Identifier("NPM", "@angular", "animations", VERSION))
        val angularCurationsFile = root.resolve("NPM").resolve("%40angular").resolve("animations.yml")
        val angularCuration = createCuration(packageAngular.id)
        angularCurationsFile.writeValue(listOf(angularCuration))

        val packageCloud = Package.EMPTY.copy(id = Identifier("Go", "", "cloud.google.com/go", VERSION))
        val cloudCurationsFile = root.resolve("Go").resolve("_").resolve("cloud.google.com%2Fgo.yml")
        val cloudCuration = createCuration(packageCloud.id)
        cloudCurationsFile.writeValue(listOf(cloudCuration))

        val provider = createProvider(root)

        val curations = provider.getCurationsFor(listOf(packageAngular, packageCloud))

        curations shouldContainExactlyInAnyOrder listOf(angularCuration, cloudCuration)
    }
})

private const val PACKAGE_TYPE = "Maven"
private const val NAMESPACE = "org.apache.commons"
private const val NAME = "commons-lang3"
private const val VERSION = "3.12.0"

private val packageLang = Package.EMPTY.copy(id = Identifier(PACKAGE_TYPE, NAMESPACE, NAME, VERSION))
private val packageText = Package.EMPTY.copy(id = Identifier(PACKAGE_TYPE, NAMESPACE, "commons-text", "1.10.0"))

/**
 * Create a [DirPackageCurationProvider] test instance using the official plugin mechanism that looks up curations in
 * the specified [root] folder.
 */
private fun createProvider(root: File): PackageCurationProvider {
    val config = ProviderPluginConfiguration(
        type = "Dir",
        options = mapOf("path" to root.absolutePath)
    )

    return PackageCurationProviderFactory.create(listOf(config)).single().second
}

/**
 * Return a test [PackageCuration] for a package with the given [id].
 */
private fun createCuration(id: Identifier): PackageCuration =
    PackageCuration(
        id = id,
        data = PackageCurationData(
            comment = "This is a curation for package ${id.toCoordinates()}.",
            purl = id.toPurl()
        )
    )
