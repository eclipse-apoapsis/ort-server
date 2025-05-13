/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.maps.shouldContainAll
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.Instant

import org.eclipse.apoapsis.ortserver.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.ProviderPluginConfiguration
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.runs.PackageManagerConfiguration
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToOrt
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerOrtConfig
import org.eclipse.apoapsis.ortserver.workers.common.env.EnvironmentForkHelper
import org.eclipse.apoapsis.ortserver.workers.common.env.config.ResolvedEnvironmentConfig
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.SecretVariableDefinition
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.SimpleVariableDefinition

import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.PackageCurationData
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsInfoCurationData
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Curations
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.IssueResolution
import org.ossreviewtoolkit.model.config.IssueResolutionReason
import org.ossreviewtoolkit.model.config.LicenseChoices
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.model.config.LicenseFindingCurationReason
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.PackageLicenseChoice
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason
import org.ossreviewtoolkit.model.config.RepositoryAnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.config.RuleViolationResolution
import org.ossreviewtoolkit.model.config.RuleViolationResolutionReason
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.config.ScopeExcludeReason
import org.ossreviewtoolkit.model.config.VulnerabilityResolution
import org.ossreviewtoolkit.model.config.VulnerabilityResolutionReason
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.writeValue
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.ort.createOrtTempDir
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseChoice
import org.ossreviewtoolkit.utils.spdx.toSpdx

private val projectDir = File("src/test/resources/mavenProject/").absoluteFile

class AnalyzerRunnerTest : WordSpec({
    fun createWorkerContext(
        providerPluginConfigs: List<ProviderPluginConfiguration> = emptyList(),
        resolvedProviderPluginConfigs: List<ProviderPluginConfiguration> = providerPluginConfigs,
        tempDir: File = tempdir()
    ): WorkerContext =
        mockk {
            every { createTempDir() } returns tempDir
            coEvery { resolveProviderPluginConfigSecrets(providerPluginConfigs) } returns resolvedProviderPluginConfigs
        }

    suspend fun run(
        context: WorkerContext = createWorkerContext(),
        inputDir: File = projectDir,
        config: AnalyzerJobConfiguration = AnalyzerJobConfiguration(),
        environmentConfig: ResolvedEnvironmentConfig = ResolvedEnvironmentConfig(),
        runner: AnalyzerRunner = AnalyzerRunner(ConfigFactory.empty())
    ): OrtResult =
        runner.run(context, inputDir, config, environmentConfig)

    afterSpec {
        unmockkAll()
    }

    "run" should {
        "return the correct repository information" {
            val result = run().repository

            result.config shouldBe RepositoryConfiguration(
                analyzer = RepositoryAnalyzerConfiguration(
                    allowDynamicVersions = true,
                    skipExcluded = true
                ),
                excludes = Excludes(
                    paths = listOf(
                        PathExclude(
                            pattern = "**/path",
                            reason = PathExcludeReason.EXAMPLE_OF,
                            comment = "This is only an example path exclude."
                        )
                    ),
                    scopes = listOf(
                        ScopeExclude(
                            pattern = "test.*",
                            reason = ScopeExcludeReason.TEST_DEPENDENCY_OF,
                            comment = "This is only an example scope exclude."
                        )
                    )
                ),
                resolutions = Resolutions(
                    issues = listOf(
                        IssueResolution(
                            message = "Error message .*",
                            reason = IssueResolutionReason.SCANNER_ISSUE,
                            comment = "This is only an example issue resolution."
                        )
                    ),
                    ruleViolations = listOf(
                        RuleViolationResolution(
                            message = "Rule Violation .*",
                            reason = RuleViolationResolutionReason.EXAMPLE_OF_EXCEPTION,
                            comment = "This is only an example rule violation resolution."
                        )
                    ),
                    vulnerabilities = listOf(
                        VulnerabilityResolution(
                            id = "CVE-ID-1234",
                            reason = VulnerabilityResolutionReason.INEFFECTIVE_VULNERABILITY,
                            comment = "This is only an example vulnerability resolution."
                        )
                    )
                ),
                curations = Curations(
                    packages = listOf(
                        PackageCuration(
                            id = Identifier("Maven:org.example:name:1.0.0"),
                            data = PackageCurationData(
                                comment = "This is only an example curation.",
                                vcs = VcsInfoCurationData(
                                    type = VcsType.GIT,
                                    url = "https://example.org/name.git",
                                    revision = "123456789"
                                )
                            )
                        )
                    ),
                    licenseFindings = listOf(
                        LicenseFindingCuration(
                            path = "README.md",
                            lineCount = 1,
                            detectedLicense = "GPL-1.0-or-later".toSpdx(),
                            concludedLicense = "NONE".toSpdx(),
                            reason = LicenseFindingCurationReason.DOCUMENTATION_OF,
                            comment = "This is only an example license finding curation."
                        )
                    )
                ),
                packageConfigurations = listOf(
                    PackageConfiguration(
                        id = Identifier("Maven:org.example:name:1.0.0"),
                        sourceArtifactUrl = "https://example.org/name-1.0.0-sources.jar"
                    )
                ),
                licenseChoices = LicenseChoices(
                    repositoryLicenseChoices = listOf(
                        SpdxLicenseChoice(
                            given = "LicenseRef-a OR LicenseRef-b".toSpdx(),
                            choice = "LicenseRef-b".toSpdx()
                        )
                    ),
                    packageLicenseChoices = listOf(
                        PackageLicenseChoice(
                            packageId = Identifier("Maven:org.example:name:1.0.0"),
                            licenseChoices = listOf(
                                SpdxLicenseChoice(
                                    given = "LicenseRef-a OR LicenseRef-b".toSpdx(),
                                    choice = "LicenseRef-a".toSpdx()
                                )
                            )
                        )
                    )
                )
            )

            result.vcs shouldNotBe VcsInfo.EMPTY
            result.vcsProcessed shouldNotBe VcsInfo.EMPTY
            result.nestedRepositories should beEmpty()
        }

        "use the .ort.yml defined by repositoryConfigPath" {
            val repository = run(config = AnalyzerJobConfiguration(repositoryConfigPath = ".custom.ort.yml")).repository

            repository.config shouldBe RepositoryConfiguration(
                analyzer = RepositoryAnalyzerConfiguration(
                    allowDynamicVersions = true,
                    skipExcluded = true
                ),
                excludes = Excludes(
                    paths = listOf(
                        PathExclude(
                            pattern = "**/custom",
                            reason = PathExcludeReason.EXAMPLE_OF,
                            comment = "This is an example path exclude from a custom .ort.yml file."
                        )
                    )
                )
            )
        }

        "fail if repositoryConfigPath points to a file outside the analyzed directory" {
            shouldThrow<IllegalArgumentException> {
                run(config = AnalyzerJobConfiguration(repositoryConfigPath = "../.ort.yml"))
            }
        }

        "pass all the properties to ORT Analyzer" {
            val enabledPackageManagers = listOf("conan", "npm")
            val disabledPackageManagers = listOf("maven")
            val packageManagerOptions = mapOf("conan" to PackageManagerConfiguration(listOf("npm")))
            val packageCurationProviderConfigs = listOf(ProviderPluginConfiguration(type = "OrtConfig"))

            val config = AnalyzerJobConfiguration(
                allowDynamicVersions = true,
                enabledPackageManagers = enabledPackageManagers,
                disabledPackageManagers = disabledPackageManagers,
                packageCurationProviders = packageCurationProviderConfigs,
                packageManagerOptions = packageManagerOptions,
                skipExcluded = true
            )

            val result = run(
                context = createWorkerContext(packageCurationProviderConfigs),
                config = config
            )
            val analyzerResult = result.analyzer.shouldNotBeNull()

            analyzerResult.config shouldBe AnalyzerConfiguration(
                true,
                enabledPackageManagers,
                disabledPackageManagers,
                packageManagerOptions.map { entry -> entry.key to entry.value.mapToOrt() }.toMap(),
                true
            )

            result.resolvedConfiguration.packageCurations.map { it.provider.id } should
                    containExactly("RepositoryConfiguration", "OrtConfig")
        }

        "return an unmanaged project for a directory with only an empty subdirectory" {
            val inputDir = createOrtTempDir().resolve("project")
            inputDir.resolve("subdirectory").safeMkdirs()

            val result = run(inputDir = inputDir).analyzer?.result

            result.shouldNotBeNull()
            result.projects.map { it.id } should containExactly(Identifier("Unmanaged::project"))
        }

        "start a forked process if custom environment variables are provided" {
            val exchangeDir = tempdir()
            val inputDir = File("some/folder/to/analyze")

            val secret1 = mockk<Secret>()
            val secret2 = mockk<Secret>()
            val environmentConfig = ResolvedEnvironmentConfig(
                environmentVariables = setOf(
                    SecretVariableDefinition("MY_ENV_VAR", secret1),
                    SecretVariableDefinition("ANOTHER_ENV_VAR", secret2)
                )
            )

            val context = createWorkerContext(tempDir = exchangeDir)

            val processStream = mockk<OutputStream> {
                every { close() } just runs
            }

            mockkObject(EnvironmentForkHelper)
            every { EnvironmentForkHelper.prepareFork(processStream) } just runs
            val process = createProcessMock(processStream)

            val processBuilder = mockk<ProcessBuilder> {
                every { start() } returns process
                every { command() } returns listOf("some", "command")
            }

            val analyzerResult = AnalyzerResult(
                projects = setOf(
                    Project.EMPTY.copy(id = Identifier("Maven:org.example:name:1.0.0"))
                ),
                packages = setOf(
                    Package.EMPTY.copy(id = Identifier("Maven:org.example2:name2:2.0.0"))
                )
            )
            val analyzerRun = AnalyzerRun(
                startTime = Instant.now(),
                endTime = Instant.now(),
                environment = Environment(),
                config = AnalyzerConfiguration(),
                result = analyzerResult
            )
            val ortResult = OrtResult.EMPTY.copy(analyzer = analyzerRun)
            exchangeDir.resolve("analyzer-result.yml").writeValue(ortResult)

            val jobConfig = AnalyzerJobConfiguration(skipExcluded = true)

            val runner = spyk(AnalyzerRunner(ConfigFactory.empty()))
            coEvery {
                runner.createProcessBuilder(context, exchangeDir, inputDir, environmentConfig)
            } returns processBuilder

            val result = run(
                context,
                config = jobConfig,
                environmentConfig = environmentConfig,
                inputDir = inputDir,
                runner = runner
            )

            result shouldBe ortResult
            val persistedConfig = exchangeDir.resolve("analyzer-config.json").readValue<AnalyzerJobConfiguration>()
            persistedConfig shouldBe jobConfig

            verify {
                processBuilder.start()
                process.waitFor()
                EnvironmentForkHelper.prepareFork(processStream)
                processStream.close()
            }
        }

        "handle errors from the forked process" {
            val exchangeDir = tempdir()
            val inputDir = File("analyze/this/folder")

            val context = createWorkerContext(tempDir = exchangeDir)

            val process = createProcessMock()
            val processBuilder = mockk<ProcessBuilder> {
                every { start() } returns process
                every { command() } returns listOf("some", "command")
            }

            val runner = spyk(AnalyzerRunner(ConfigFactory.empty()))
            coEvery {
                runner.createProcessBuilder(any(), any(), any(), any())
            } returns processBuilder

            val environmentConfig = ResolvedEnvironmentConfig(
                environmentVariables = setOf(SecretVariableDefinition("MY_ENV_VAR", mockk()))
            )

            val forkError = "test.ForkException: Something went terribly wrong."
            exchangeDir.resolve("analyzer-error.txt").writeText(forkError)

            val exception = shouldThrow<IOException> {
                run(context, inputDir = inputDir, environmentConfig = environmentConfig, runner = runner)
            }

            exception.message shouldContain forkError
        }

        "handle errors from the forked process when the error file does not exist" {
            val exchangeDir = tempdir()
            val inputDir = File("analyze/this/folder")

            val context = createWorkerContext(tempDir = exchangeDir)

            val process = createProcessMock()
            val processBuilder = mockk<ProcessBuilder> {
                every { start() } returns process
                every { command() } returns listOf("some", "command")
            }

            val runner = spyk(AnalyzerRunner(ConfigFactory.empty()))
            coEvery {
                runner.createProcessBuilder(any(), any(), any(), any())
            } returns processBuilder

            val environmentConfig = ResolvedEnvironmentConfig(
                environmentVariables = setOf(SecretVariableDefinition("MY_ENV_VAR", mockk()))
            )

            val exception = shouldThrow<IOException> {
                run(context, inputDir = inputDir, environmentConfig = environmentConfig, runner = runner)
            }

            exception.message shouldContain "The forked process died"
        }

        "resolve package curation provider secrets when running in process" {
            val inputDir = createOrtTempDir().resolve("project").safeMkdirs()

            val packageCurationProviderConfigs = listOf(
                ProviderPluginConfiguration(
                    type = "type1",
                    options = mapOf("path" to "path1"),
                    secrets = mapOf("secret1" to "ref1")
                ),
                ProviderPluginConfiguration(
                    type = "type2",
                    options = mapOf("path" to "path2"),
                    secrets = mapOf("secret2" to "ref2")
                )
            )

            val resolvedPackageCurationProviderConfigs = listOf(
                ProviderPluginConfiguration(
                    type = "type1",
                    options = mapOf("path" to "path1"),
                    secrets = mapOf("secret1" to "value1")
                ),
                ProviderPluginConfiguration(
                    type = "type2",
                    options = mapOf("path" to "path2"),
                    secrets = mapOf("secret2" to "value2")
                )
            )

            val config = AnalyzerJobConfiguration(packageCurationProviders = packageCurationProviderConfigs)

            val runner = spyk(AnalyzerRunner(ConfigFactory.empty()))

            run(
                context = createWorkerContext(
                    providerPluginConfigs = packageCurationProviderConfigs,
                    resolvedProviderPluginConfigs = resolvedPackageCurationProviderConfigs
                ),
                config = config,
                inputDir = inputDir,
                runner = runner
            )

            verify(exactly = 1) {
                runner.runInProcess(
                    inputDir,
                    config.copy(packageCurationProviders = resolvedPackageCurationProviderConfigs)
                )
            }
        }

        "resolve package curation provider secrets when running in a fork" {
            val inputDir = createOrtTempDir().resolve("project").safeMkdirs()

            val packageCurationProviderConfigs = listOf(
                ProviderPluginConfiguration(
                    type = "type1",
                    options = mapOf("path" to "path1"),
                    secrets = mapOf("secret1" to "ref1")
                ),
                ProviderPluginConfiguration(
                    type = "type2",
                    options = mapOf("path" to "path2"),
                    secrets = mapOf("secret2" to "ref2")
                )
            )

            val resolvedPackageCurationProviderConfigs = listOf(
                ProviderPluginConfiguration(
                    type = "type1",
                    options = mapOf("path" to "path1"),
                    secrets = mapOf("secret1" to "value1")
                ),
                ProviderPluginConfiguration(
                    type = "type2",
                    options = mapOf("path" to "path2"),
                    secrets = mapOf("secret2" to "value2")
                )
            )

            val environmentConfig = ResolvedEnvironmentConfig(
                environmentVariables = setOf(
                    SecretVariableDefinition("ENV_VAR", mockk())
                )
            )

            val process = createProcessMock()
            val processBuilder = mockk<ProcessBuilder> {
                every { start() } returns process
                every { command() } returns listOf("some", "command")
            }

            val config = AnalyzerJobConfiguration(packageCurationProviders = packageCurationProviderConfigs)

            val runner = spyk(AnalyzerRunner(ConfigFactory.empty()))

            coEvery { runner.createProcessBuilder(any(), any(), any(), any()) } returns processBuilder

            // Expect an exception because making the run succeed would require additional setup which is not required
            // for this test. The relevant part is that the `runForked` call can be verified below.
            shouldThrow<IOException> {
                run(
                    context = createWorkerContext(
                        providerPluginConfigs = packageCurationProviderConfigs,
                        resolvedProviderPluginConfigs = resolvedPackageCurationProviderConfigs
                    ),
                    config = config,
                    inputDir = inputDir,
                    runner = runner,
                    environmentConfig = environmentConfig
                )
            }

            coVerify(exactly = 1) {
                runner.runForked(
                    any(),
                    inputDir,
                    config.copy(packageCurationProviders = resolvedPackageCurationProviderConfigs),
                    environmentConfig
                )
            }
        }
    }

    "createProcessBuilder" should {
        "create a process builder with the correct environment" {
            val secret1 = mockk<Secret>()
            val secret2 = mockk<Secret>()
            val environmentConfig = ResolvedEnvironmentConfig(
                environmentVariables = setOf(
                    SecretVariableDefinition("MY_ENV_VAR", secret1),
                    SecretVariableDefinition("ANOTHER_ENV_VAR", secret2),
                    SimpleVariableDefinition("SIMPLE_ENV_VAR", "simpleValue"),
                    SimpleVariableDefinition("ANOTHER_SIMPLE_ENV_VAR", "anotherSimpleValue")
                )
            )

            val secretsToResolve = mutableListOf<Secret>()
            val context = mockk<WorkerContext> {
                coEvery { resolveSecrets(*varargAll { secretsToResolve.add(it) }) } answers {
                    mapOf(
                        secret1 to "mySecret",
                        secret2 to "anotherSecret"
                    )
                }
            }

            val exchangeDir = File("exchangeDir")
            val inputDir = File("inputDir")
            val expectedCommands = listOf(
                "/bin/sh",
                "-c",
                "exec java -cp ${System.getProperty("java.class.path")} " +
                        "org.eclipse.apoapsis.ortserver.workers.analyzer.AnalyzerRunner " +
                        "${exchangeDir.absolutePath} ${inputDir.absolutePath}"
            )
            val runner = AnalyzerRunner(ConfigFactory.empty())
            val processBuilder = runner.createProcessBuilder(context, exchangeDir, inputDir, environmentConfig)

            secretsToResolve should containExactlyInAnyOrder(secret1, secret2)

            processBuilder.environment() shouldContainAll mapOf(
                "MY_ENV_VAR" to "mySecret",
                "ANOTHER_ENV_VAR" to "anotherSecret",
                "SIMPLE_ENV_VAR" to "simpleValue",
                "ANOTHER_SIMPLE_ENV_VAR" to "anotherSimpleValue"
            )

            processBuilder.command() shouldContainExactly expectedCommands
        }

        "support a custom command to fork the process" {
            val configMap = mapOf(
                "analyzer.forkCommands" to "myLauncher*run on*\${CLASSPATH}*this: \${LAUNCH}*--fast",
                "analyzer.forkCommandSeparator" to "*"
            )
            val config = ConfigFactory.parseMap(configMap)

            val context = mockk<WorkerContext> {
                coEvery { resolveSecrets(*anyVararg()) } returns emptyMap()
            }

            val exchangeDir = File("exchangeDir")
            val inputDir = File("inputDir")
            val expectedCommands = listOf(
                "myLauncher",
                "run on",
                System.getProperty("java.class.path"),
                "this: org.eclipse.apoapsis.ortserver.workers.analyzer.AnalyzerRunner ${exchangeDir.absolutePath} " +
                        inputDir.absolutePath,
                "--fast"
            )

            val runner = AnalyzerRunner(config)
            val processBuilder =
                runner.createProcessBuilder(context, exchangeDir, inputDir, ResolvedEnvironmentConfig())

            processBuilder.command() shouldContainExactly expectedCommands
        }
    }

    "main" should {
        mockkObject(EnvironmentForkHelper)
        every { EnvironmentForkHelper.setupFork(any()) } just runs

        "produce a correct ORT result" {
            val exchangeDir = tempdir()

            val enabledPackageManagers = listOf("conan", "npm")
            val disabledPackageManagers = listOf("maven")
            val packageManagerOptions = mapOf("conan" to PackageManagerConfiguration(listOf("npm")))
            val config = AnalyzerJobConfiguration(
                allowDynamicVersions = true,
                enabledPackageManagers = enabledPackageManagers,
                disabledPackageManagers = disabledPackageManagers,
                packageCurationProviders = listOf(ProviderPluginConfiguration(type = "OrtConfig")),
                packageManagerOptions = packageManagerOptions,
                skipExcluded = true
            )

            val configFile = exchangeDir.resolve("analyzer-config.json")
            configFile.writeValue(config)

            AnalyzerRunner.main(arrayOf(exchangeDir.absolutePath, projectDir.absolutePath))

            val ortResult = exchangeDir.resolve("analyzer-result.yml").readValue<OrtResult>()
            val analyzerResult = ortResult.analyzer.shouldNotBeNull()

            analyzerResult.config shouldBe AnalyzerConfiguration(
                true,
                enabledPackageManagers,
                disabledPackageManagers,
                packageManagerOptions.map { entry -> entry.key to entry.value.mapToOrt() }.toMap(),
                true
            )
        }

        "produce a failure result in case of an error" {
            val exchangeDir = tempdir()
            exchangeDir.resolve("analyzer-config.json").writeValue(AnalyzerJobConfiguration())

            AnalyzerRunner.main(arrayOf(exchangeDir.absolutePath, "non-existing-directory"))

            val errorResult = exchangeDir.resolve("analyzer-error.txt").readText()
            errorResult shouldContain "java.lang.IllegalArgumentException"
        }

        "set up the ORT environment" {
            mockkObject(WorkerOrtConfig)
            val workerOrtConfigMock = mockk<WorkerOrtConfig> {
                every { setUpOrtEnvironment() } just runs
            }
            every { WorkerOrtConfig.create() } returns workerOrtConfigMock

            val exchangeDir = tempdir()
            exchangeDir.resolve("analyzer-config.json").writeValue(AnalyzerJobConfiguration())

            AnalyzerRunner.main(arrayOf(exchangeDir.absolutePath, "non-existing-directory"))

            verify {
                workerOrtConfigMock.setUpOrtEnvironment()
                EnvironmentForkHelper.setupFork(System.`in`)
            }
        }
    }
})

/**
 * Create a mock for a [Process] that is prepared to expect some standard interactions.
 */
private fun createProcessMock(pipeStream: OutputStream = ByteArrayOutputStream()): Process =
    mockk {
        every { waitFor() } returns 0
        every { outputStream } returns pipeStream
    }
