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

package org.eclipse.apoapsis.ortserver.workers.reporter

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.file.exist
import io.kotest.matchers.file.shouldContainFile
import io.kotest.matchers.file.shouldContainNFiles
import io.kotest.matchers.maps.containAnyKeys
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.sequences.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.types.beInstanceOf

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot

import java.io.File
import java.nio.file.Files

import org.eclipse.apoapsis.ortserver.workers.common.OrtTestData

import org.ossreviewtoolkit.downloader.DownloadException
import org.ossreviewtoolkit.downloader.Downloader
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.PluginConfiguration
import org.ossreviewtoolkit.model.licenses.LicenseCategorization
import org.ossreviewtoolkit.model.licenses.LicenseCategory
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.unpackZip
import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression

private const val PROJECT_ID_FILENAME = "Maven-com.vdurmont-semver4j-2.0.1.zip"
private const val PROJECT_NAME = "semver4j"
private const val PROJECT_VERSION = "2.0.1"

private const val PACKAGE_ID_FILENAME = "Maven-org.codehaus.jettison-jettison-1.5.4.zip"
private const val PACKAGE_NAME = "jettison"
private const val PACKAGE_VERSION = "1.5.4"

private const val TEST_CONTENT_SRC = "testContentSrc"
private const val TEST_CONTENT_IGNORE = "testContentIgnore"
private const val TEST_CONTENT_TRAVIS = "testContentTravis"
private const val TEST_CONTENT_CHANGELOG = "testContentChangelog"
private const val TEST_CONTENT_POM = "testContentPom"

class SourceCodeBundleReporterTest : WordSpec({
    "The SourceCodeBundleReporter" should {
        "download source files and create a bundle with a correct name" {
            val projectContent = mapOf(
                "src" to TEST_CONTENT_SRC,
                ".gitignore" to TEST_CONTENT_IGNORE,
                ".travis.yml" to TEST_CONTENT_TRAVIS,
                "CHANGELOG.md" to TEST_CONTENT_CHANGELOG,
                "pom.xml" to TEST_CONTENT_POM
            )
            val downloader = mockk<Downloader>()
            downloader.expectDownload(project.toPackage(), projectContent)

            val reporter = SourceCodeBundleReporter(downloader)

            val input = getReporterInput()
            val outputDir = tempdir()

            val reportFileResults = reporter.generateReport(
                input,
                outputDir,
                PluginConfiguration(options = mapOf(SourceCodeBundleReporter.PACKAGE_TYPE_PROPERTY to "PROJECT"))
            )

            reportFileResults.shouldBeSingleton {
                it shouldBeSuccess { codeBundleFile ->
                    codeBundleFile.name shouldBe SOURCE_BUNDLE_FILE_NAME

                    val projectContents = codeBundleFile.checkAndUnpackBundle(
                        outputDir,
                        PROJECT_ID_FILENAME,
                        PROJECT_NAME,
                        PROJECT_VERSION
                    )

                    projectContents shouldContainNFiles 5
                    projectContents.resolve("src").readText() shouldBe TEST_CONTENT_SRC
                    projectContents.resolve(".gitignore").readText() shouldBe TEST_CONTENT_IGNORE
                    projectContents.resolve(".travis.yml").readText() shouldBe TEST_CONTENT_TRAVIS
                    projectContents.resolve("CHANGELOG.md").readText() shouldBe TEST_CONTENT_CHANGELOG
                    projectContents.resolve("pom.xml").readText() shouldBe TEST_CONTENT_POM
                }
            }
        }

        "delete all the downloaded files even in case of a failure" {
            val downloader = mockk<Downloader>()

            val downloaderDirSlot = slot<File>()

            every {
                downloader.download(any(), outputDirectory = capture(downloaderDirSlot))
            } answers {
                val projectDir = downloaderDirSlot.captured
                Files.createDirectories(projectDir.toPath())
                projectDir.resolve("pom.xml").writeBytes(TEST_CONTENT_POM.toByteArray())
                throw DownloadException("The repository cannot be downloaded")
            }

            val reporter = SourceCodeBundleReporter(downloader)

            val input = getReporterInput()
            val outputDir = tempdir()
            val bundleTmpOutputDir = outputDir.resolve(SOURCE_BUNDLE_SUB_DIR)

            val reportFileResults = reporter.generateReport(
                input,
                outputDir,
                PluginConfiguration(options = mapOf(SourceCodeBundleReporter.PACKAGE_TYPE_PROPERTY to "PROJECT"))
            )

            reportFileResults.shouldBeSingleton {
                it.shouldBeFailure<DownloadException>()
            }

            bundleTmpOutputDir shouldNot exist()
            outputDir.walk().maxDepth(1).filter { it.isFile } should beEmpty()
        }

        "be found by the service loader" {
            val reporter = SourceCodeBundleReporter()

            Reporter.ALL should containAnyKeys(reporter.type)
            Reporter.ALL[reporter.type] should beInstanceOf<SourceCodeBundleReporter>()
        }

        "add only included sources from packages from included license categories" {
            val pkgContent = mapOf(
                "src" to TEST_CONTENT_SRC,
                ".gitignore" to TEST_CONTENT_IGNORE,
                ".travis.yml" to TEST_CONTENT_TRAVIS,
                "CHANGELOG.md" to TEST_CONTENT_CHANGELOG,
                "pom.xml" to TEST_CONTENT_POM
            )
            val downloader = mockk<Downloader>()
            downloader.expectDownload(pkg, pkgContent)

            val reporter = SourceCodeBundleReporter(downloader)

            val input = getReporterInput()
            val outputDir = tempdir()

            val reportFileResults = reporter.generateReport(
                input,
                outputDir,
                PluginConfiguration(
                    options = mapOf(
                        SourceCodeBundleReporter.PACKAGE_TYPE_PROPERTY to "PROJECT,PACKAGE",
                        SourceCodeBundleReporter.INCLUDED_LICENSE_CATEGORIES_PROPERTY to "include-in-source-code-bundle"
                    )
                )
            )

            reportFileResults.shouldBeSingleton {
                it shouldBeSuccess { codeBundleFile ->
                    codeBundleFile.name shouldBe SOURCE_BUNDLE_FILE_NAME

                    val packageContents = codeBundleFile.checkAndUnpackBundle(
                        outputDir,
                        PACKAGE_ID_FILENAME,
                        PACKAGE_NAME,
                        PACKAGE_VERSION
                    )

                    packageContents shouldContainNFiles 5
                    packageContents.resolve("src").readText() shouldBe TEST_CONTENT_SRC
                    packageContents.resolve(".gitignore").readText() shouldBe TEST_CONTENT_IGNORE
                    packageContents.resolve(".travis.yml").readText() shouldBe TEST_CONTENT_TRAVIS
                    packageContents.resolve("CHANGELOG.md").readText() shouldBe TEST_CONTENT_CHANGELOG
                    packageContents.resolve("pom.xml").readText() shouldBe TEST_CONTENT_POM
                }
            }
        }

        "handle a path to a sub folder in VCS info correctly" {
            val subPath = "includeOnlyThis"
            val pkgContent = mapOf(
                "test" to "someTestContent",
                "$subPath/src" to TEST_CONTENT_SRC,
                "$subPath/CHANGELOG.md" to TEST_CONTENT_CHANGELOG,
                "foo" to "bar"
            )
            val repoProvenance = RepositoryProvenance(
                vcsInfoPackage.copy(path = subPath),
                "some-resolved-revision"
            )
            val downloader = mockk<Downloader>()
            downloader.expectDownload(pkg, pkgContent, repoProvenance)

            val reporter = SourceCodeBundleReporter(downloader)

            val input = getReporterInput()
            val outputDir = tempdir()

            val reportFileResults = reporter.generateReport(
                input,
                outputDir,
                PluginConfiguration(
                    options = mapOf(
                        SourceCodeBundleReporter.PACKAGE_TYPE_PROPERTY to "PACKAGE"
                    )
                )
            )

            reportFileResults.shouldBeSingleton {
                it shouldBeSuccess { codeBundleFile ->
                    codeBundleFile.name shouldBe SOURCE_BUNDLE_FILE_NAME

                    val packageContents = codeBundleFile.checkAndUnpackBundle(
                        outputDir,
                        PACKAGE_ID_FILENAME,
                        PACKAGE_NAME,
                        PACKAGE_VERSION
                    ).resolve(subPath)

                    packageContents shouldContainNFiles 2
                    packageContents.resolve("src").readText() shouldBe TEST_CONTENT_SRC
                    packageContents.resolve("CHANGELOG.md").readText() shouldBe TEST_CONTENT_CHANGELOG
                }
            }
        }

        "handle a path to a single file in VCS info correctly" {
            val subPath = "this/is/the/only/relevant/file.txt"
            val pkgContent = mapOf(
                "test" to "someTestContent",
                subPath to TEST_CONTENT_SRC,
                "foo" to "bar"
            )
            val repoProvenance = RepositoryProvenance(
                vcsInfoPackage.copy(path = subPath),
                "some-resolved-revision"
            )
            val downloader = mockk<Downloader>()
            downloader.expectDownload(pkg, pkgContent, repoProvenance)

            val reporter = SourceCodeBundleReporter(downloader)

            val input = getReporterInput()
            val outputDir = tempdir()

            val reportFileResults = reporter.generateReport(
                input,
                outputDir,
                PluginConfiguration(
                    options = mapOf(
                        SourceCodeBundleReporter.PACKAGE_TYPE_PROPERTY to "PACKAGE"
                    )
                )
            )

            reportFileResults.shouldBeSingleton {
                it shouldBeSuccess { codeBundleFile ->
                    codeBundleFile.name shouldBe SOURCE_BUNDLE_FILE_NAME

                    val packageContents = codeBundleFile.checkAndUnpackBundle(
                        outputDir,
                        PACKAGE_ID_FILENAME,
                        PACKAGE_NAME,
                        PACKAGE_VERSION
                    )

                    packageContents shouldContainNFiles 1
                    packageContents.resolve(subPath).readText() shouldBe TEST_CONTENT_SRC
                }
            }
        }
    }
})

/**
 * Unpack this source code bundle file to the given [outputDir] and check whether it has the expected folder structure
 * containing components for the [pkgFile], [pkgName], and [pkgVersion]. Return the sub folder with the actual
 * content.
 */
private fun File.checkAndUnpackBundle(outputDir: File, pkgFile: String, pkgName: String, pkgVersion: String): File {
    unpackZip(outputDir)
    outputDir shouldContainNFiles 2
    outputDir shouldContainFile pkgFile

    outputDir.resolve(pkgFile).unpackZip(outputDir)
    outputDir shouldContainFile pkgName
    outputDir.resolve(pkgName) shouldContainFile pkgVersion

    return outputDir.resolve(pkgName).resolve(pkgVersion)
}

private val vcsInfoPackage = VcsInfo(
    type = VcsType.GIT,
    url = "https://github.com/jettison-json/jettison.git",
    revision = "1.5.4",
    path = ""
)

private val pkg = OrtTestData.pkg.copy(
    id = Identifier("Maven:org.codehaus.jettison:jettison:1.5.4"),
    vcs = vcsInfoPackage,
    vcsProcessed = vcsInfoPackage
)

private val vcsInfoProject = VcsInfo(
    type = VcsType.GIT,
    url = "https://github.com/vdurmont/semver4j.git",
    revision = "master",
    path = ""
)

private val project = OrtTestData.project.copy(
    id = Identifier("Maven:com.vdurmont:semver4j:2.0.1"),
    vcs = vcsInfoProject,
    vcsProcessed = vcsInfoProject,
    scopeNames = null,
    scopeDependencies = setOf(Scope("compile", setOf(PackageReference(pkg.id))))
)

private fun getReporterInput(): ReporterInput {
    val repository = OrtTestData.repository.copy(vcs = vcsInfoProject, vcsProcessed = vcsInfoProject)

    val result = AnalyzerResult(
        projects = setOf(project),
        packages = setOf(pkg),
        issues = mapOf(OrtTestData.pkgIdentifier to listOf(OrtTestData.issue)),
        dependencyGraphs = mapOf("Maven" to OrtTestData.dependencyGraph)
    )

    val analyzerRun = OrtTestData.analyzerRun.copy(
        result = result
    )

    val ortResult = OrtTestData.result.copy(analyzer = analyzerRun, repository = repository)
    val input = ReporterInput(
        ortResult = ortResult,
        licenseClassifications = LicenseClassifications(
            categories = listOf(LicenseCategory("include-in-source-code-bundle")),
            categorizations = listOf(
                LicenseCategorization(
                    id = SpdxSingleLicenseExpression.parse("LicenseRef-package-declared"),
                    categories = setOf("include-in-source-code-bundle")
                )
            )
        )
    )

    return input
}

/**
 * Prepare this mock [Downloader] to expect an invocation of the [Downloader.download] function with the given [pkg].
 * The mock reacts by populating the download directory with the given [content] and returns the given [provenance].
 */
private fun Downloader.expectDownload(pkg: Package, content: Map<String, String>, provenance: Provenance = mockk()) {
    every { download(pkg, any()) } answers {
        val projectDir = secondArg<File>()

        content.forEach { (path, fileContent) ->
            val file = projectDir.resolve(path)
            file.parentFile.mkdirs()
            file.writeText(fileContent)
        }

        provenance
    }
}
