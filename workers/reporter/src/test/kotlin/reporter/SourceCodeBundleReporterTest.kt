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

package org.ossreviewtoolkit.server.workers.reporter

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.file.shouldContainFile
import io.kotest.matchers.file.shouldContainNFiles
import io.kotest.matchers.maps.containAnyKeys
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot

import java.io.File
import java.nio.file.Files

import org.ossreviewtoolkit.downloader.DownloadException
import org.ossreviewtoolkit.downloader.Downloader
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.PluginConfiguration
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.server.workers.common.OrtTestData
import org.ossreviewtoolkit.utils.common.unpackZip

private const val PACKAGE_ID_FILENAME = "Maven-com.vdurmont-semver4j-2.0.1.zip"
private const val PROJECT_NAME = "semver4j"
private const val PROJECT_VERSION = "2.0.1"

private const val TEST_CONTENT_SRC = "testContentSrc"
private const val TEST_CONTENT_IGNORE = "testContentIgnore"
private const val TEST_CONTENT_TRAVIS = "testContentTravis"
private const val TEST_CONTENT_CHANGELOG = "testContentChangelog"
private const val TEST_CONTENT_POM = "testContentPom"

class SourceCodeBundleReporterTest : WordSpec({
    "The SourceCodeBundleReporter" should {
        "download source files and create a bundle with a correct name" {
            val downloader = mockDownloader()

            val reporter = SourceCodeBundleReporter(downloader)

            val input = getReporterInput()
            val outputDir = tempdir()

            val reportFiles = reporter.generateReport(
                input,
                outputDir,
                PluginConfiguration(options = mapOf(SourceCodeBundleReporter.PACKAGE_TYPE_PROPERTY to "PROJECT"))
            )
            reportFiles should haveSize(1)

            val codeBundleFile = reportFiles.single()
            codeBundleFile.name shouldBe SOURCE_BUNDLE_FILE_NAME

            codeBundleFile.unpackZip(outputDir)
            outputDir shouldContainNFiles 2
            outputDir shouldContainFile PACKAGE_ID_FILENAME

            outputDir.resolve(PACKAGE_ID_FILENAME).unpackZip(outputDir)
            outputDir shouldContainFile PROJECT_NAME
            outputDir.resolve(PROJECT_NAME) shouldContainFile PROJECT_VERSION

            val projectContents = outputDir.resolve(PROJECT_NAME).resolve(PROJECT_VERSION)
            projectContents shouldContainNFiles 5
            projectContents.resolve("src").readText() shouldBe TEST_CONTENT_SRC
            projectContents.resolve(".gitignore").readText() shouldBe TEST_CONTENT_IGNORE
            projectContents.resolve(".travis.yml").readText() shouldBe TEST_CONTENT_TRAVIS
            projectContents.resolve("CHANGELOG.md").readText() shouldBe TEST_CONTENT_CHANGELOG
            projectContents.resolve("pom.xml").readText() shouldBe TEST_CONTENT_POM
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

            shouldThrow<DownloadException> {
                reporter.generateReport(
                    input,
                    outputDir,
                    PluginConfiguration(options = mapOf(SourceCodeBundleReporter.PACKAGE_TYPE_PROPERTY to "PROJECT"))
                )
            }

            outputDir.listFiles()?.size shouldBe 0
        }

        "be found by the service loader" {
            val reporter = SourceCodeBundleReporter()

            Reporter.ALL should containAnyKeys(reporter.type)
            Reporter.ALL[reporter.type] should beInstanceOf<SourceCodeBundleReporter>()
        }
    }
})

private fun getReporterInput(): ReporterInput {
    val vcsInfo = VcsInfo(
        type = VcsType.GIT,
        url = "https://github.com/vdurmont/semver4j.git",
        revision = "master",
        path = ""
    )

    val project = OrtTestData.project.copy(
        id = Identifier("Maven:com.vdurmont:semver4j:2.0.1"),
        vcs = vcsInfo,
        vcsProcessed = vcsInfo
    )

    val repository = OrtTestData.repository.copy(vcs = vcsInfo, vcsProcessed = vcsInfo)

    val result = AnalyzerResult(
        projects = setOf(project),
        packages = setOf(OrtTestData.pkg),
        issues = mapOf(OrtTestData.pkgIdentifier to listOf(OrtTestData.issue)),
        dependencyGraphs = mapOf("Maven" to OrtTestData.dependencyGraph)
    )

    val analyzerRun = OrtTestData.analyzerRun.copy(
        result = result
    )

    val ortResult = OrtTestData.result.copy(analyzer = analyzerRun, repository = repository)
    val input = ReporterInput(ortResult = ortResult)
    return input
}

private fun mockDownloader(): Downloader {
    val downloader = mockk<Downloader>()

    val downloaderDirSlot = slot<File>()

    every {
        downloader.download(any(), outputDirectory = capture(downloaderDirSlot))
    } answers {
        val projectDir = downloaderDirSlot.captured
        Files.createDirectories(projectDir.toPath())

        projectDir.resolve("src").writeBytes(TEST_CONTENT_SRC.toByteArray())
        projectDir.resolve(".gitignore").writeBytes(TEST_CONTENT_IGNORE.toByteArray())
        projectDir.resolve(".travis.yml").writeBytes(TEST_CONTENT_TRAVIS.toByteArray())
        projectDir.resolve("CHANGELOG.md").writeBytes(TEST_CONTENT_CHANGELOG.toByteArray())
        projectDir.resolve("pom.xml").writeBytes(TEST_CONTENT_POM.toByteArray())

        mockk<RepositoryProvenance>()
    }

    return downloader
}
