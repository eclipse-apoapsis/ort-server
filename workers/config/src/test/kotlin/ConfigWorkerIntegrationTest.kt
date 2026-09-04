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

package org.eclipse.apoapsis.ortserver.workers.config

import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.maps.containExactly as containEntriesExactly
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.beInstanceOf
import io.kotest.matchers.types.shouldBeInstanceOf

import org.eclipse.apoapsis.ortserver.components.adminconfig.AdminConfigService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateEventStore
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateService
import org.eclipse.apoapsis.ortserver.components.secrets.SecretService
import org.eclipse.apoapsis.ortserver.config.ConfigFileProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.ConfigSecretProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.secrets.SecretStorage
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.utils.test.Integration
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.SecretResolverService
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory

class ConfigWorkerIntegrationTest : WordSpec({
    tags(Integration)

    val dbExtension = extension(DatabaseTestExtension())

    lateinit var configWorker: ConfigWorker
    lateinit var fixtures: Fixtures

    beforeEach {
        fixtures = dbExtension.fixtures

        val config = mapOf(
            ConfigManager.CONFIG_MANAGER_SECTION to mapOf(
                ConfigManager.FILE_PROVIDER_NAME_PROPERTY to ConfigFileProviderFactoryForTesting.NAME,
                ConfigManager.SECRET_PROVIDER_NAME_PROPERTY to ConfigSecretProviderFactoryForTesting.NAME
            ),
            SecretStorage.CONFIG_PREFIX to mapOf(
                SecretStorage.NAME_PROPERTY to SecretsProviderFactoryForTesting.NAME
            )
        )

        val configManager = ConfigManager.create(ConfigFactory.parseMap(config))

        val secretStorage = SecretStorage.createStorage(configManager)

        val secretService = SecretService(
            db = dbExtension.db,
            secretRepository = fixtures.secretRepository,
            secretStorage = secretStorage
        )

        val secretResolverService = SecretResolverService.wrapSecretService(secretService)

        val workerContextFactory = WorkerContextFactory(
            configManager = configManager,
            ortRunRepository = fixtures.ortRunRepository,
            repositoryRepository = fixtures.repositoryRepository,
            secretService = secretResolverService
        )

        val adminConfigService = AdminConfigService(configManager)

        val pluginService = PluginService(
            db = dbExtension.db
        )

        val pluginTemplateService = PluginTemplateService(
            db = dbExtension.db,
            eventStore = PluginTemplateEventStore(dbExtension.db),
            pluginService = pluginService,
            organizationRepository = fixtures.organizationRepository,
            repositoryRepository = fixtures.repositoryRepository,
            adminConfigService = adminConfigService
        )

        configWorker = ConfigWorker(
            db = dbExtension.db,
            ortRunRepository = fixtures.ortRunRepository,
            contextFactory = workerContextFactory,
            adminConfigService = adminConfigService,
            pluginService = pluginService,
            pluginTemplateService = pluginTemplateService
        )
    }

    "run" should {
        "succeed if no admin config and validation script files exist" {
            val ortRunId = fixtures.ortRun.id

            val result = configWorker.run(ortRunId)

            result shouldBe RunResult.Success

            // Verify that the context and job configs were resolved.
            fixtures.ortRunRepository.get(ortRunId).shouldNotBeNull {
                resolvedJobConfigContext shouldBe ConfigFileProviderFactoryForTesting.RESOLVED_PREFIX
                resolvedJobConfigs shouldNot beNull()
            }
        }

        "apply a successful validation script" {
            val ortRun = fixtures.createOrtRun(
                jobConfigContext = "src/test/resources/successful-validation-script",
                labels = mapOf("default" to "label")
            )

            val result = configWorker.run(ortRun.id)

            result shouldBe RunResult.Success

            // Verify that the validation script can modify the job configs, add issues, and add labels.
            fixtures.ortRunRepository.get(ortRun.id).shouldNotBeNull {
                resolvedJobConfigContext shouldBe ConfigFileProviderFactoryForTesting.RESOLVED_PREFIX +
                        "src/test/resources/successful-validation-script"

                resolvedJobConfigs.shouldNotBeNull {
                    parameters should containEntriesExactly("validation-script" to "parameter")
                }

                issues.shouldBeSingleton {
                    it.source shouldBe "validation-script"
                    it.message shouldBe "Validation script issue"
                    it.severity shouldBe Severity.HINT
                }

                labels should containEntriesExactly(
                    "default" to "label",
                    "validation-script" to "label"
                )
            }
        }

        "apply a failing validation script" {
            val ortRun = fixtures.createOrtRun(
                jobConfigContext = "src/test/resources/failing-validation-script",
                labels = mapOf("default" to "label")
            )

            val result = configWorker.run(ortRun.id)

            result.shouldBeInstanceOf<RunResult.Failed> {
                it.error should beInstanceOf<IllegalArgumentException>()
            }

            // Verify that the context was resolved but the job configs were not, an issue was added, and the labels
            // were not modified.
            fixtures.ortRunRepository.get(ortRun.id).shouldNotBeNull {
                resolvedJobConfigContext shouldBe ConfigFileProviderFactoryForTesting.RESOLVED_PREFIX +
                        "src/test/resources/failing-validation-script"

                resolvedJobConfigs should beNull()

                issues.shouldBeSingleton {
                    it.source shouldBe "validation-script"
                    it.message shouldBe "Validation script issue"
                    it.severity shouldBe Severity.ERROR
                }

                labels should containEntriesExactly("default" to "label")
            }
        }

        "validate the admin config if no validation script exists" {
            val ortRun = fixtures.createOrtRun(
                jobConfigurations = JobConfigurations(ruleSet = "non-existing")
            )

            val result = configWorker.run(ortRun.id)

            // Verify that the admin config validation failed because a non-existing rule set was used.
            result.shouldBeInstanceOf<RunResult.Failed> {
                it.error should beInstanceOf<IllegalArgumentException>()
            }

            // Verify that an issue for the root cause was created.
            fixtures.ortRunRepository.get(ortRun.id).shouldNotBeNull {
                issues.shouldBeSingleton {
                    it.message shouldContain "rule set"
                    it.message shouldContain "non-existing"
                    it.severity shouldBe Severity.ERROR
                }
            }
        }

        "validate the admin config if a validation script exists" {
            val ortRun = fixtures.createOrtRun(
                jobConfigContext = "src/test/resources/successful-validation-script",
                jobConfigurations = JobConfigurations(ruleSet = "non-existing")
            )

            val result = configWorker.run(ortRun.id)

            result.shouldBeInstanceOf<RunResult.Failed> {
                it.error should beInstanceOf<IllegalArgumentException>()
            }

            // Verify that an issue for invalid rule set was created and the issue from the validation script was kept.
            fixtures.ortRunRepository.get(ortRun.id).shouldNotBeNull {
                issues should haveSize(2)

                issues.filter { it.severity == Severity.ERROR }.shouldBeSingleton {
                    it.message shouldContain "rule set"
                    it.message shouldContain "non-existing"
                    it.severity shouldBe Severity.ERROR
                }
            }
        }

        "fail if there is an exception during validation" {
            val ortRun = fixtures.createOrtRun(
                jobConfigContext = "src/test/resources/not-compiling-validation-script"
            )

            val result = configWorker.run(ortRun.id)

            result.shouldBeInstanceOf<RunResult.Failed> {
                it.error should beInstanceOf<IllegalArgumentException>()
            }

            // Verify that an issue for the compile error was created.
            fixtures.ortRunRepository.get(ortRun.id).shouldNotBeNull {
                issues.shouldBeSingleton {
                    it.message shouldContain "Error when executing validation script"
                    it.severity shouldBe Severity.ERROR
                }
            }
        }

        "resolve the default package managers if none were configured" {
            val ortRun = fixtures.createOrtRun(
                jobConfigurations = JobConfigurations(
                    analyzer = AnalyzerJobConfiguration(
                        enabledPackageManagers = null
                    )
                )
            )

            val result = configWorker.run(ortRun.id)

            result shouldBe RunResult.Success

            // Verify that some package managers were enabled.
            fixtures.ortRunRepository.get(ortRun.id).shouldNotBeNull {
                resolvedJobConfigs.shouldNotBeNull {
                    analyzer.enabledPackageManagers.shouldNotBeNull() shouldNot beEmpty()
                }
            }
        }

        "not overwrite the configured package managers" {
            val ortRun = fixtures.createOrtRun(
                jobConfigurations = JobConfigurations(
                    analyzer = AnalyzerJobConfiguration(
                        enabledPackageManagers = listOf("Gradle", "Maven")
                    )
                )
            )

            val result = configWorker.run(ortRun.id)

            result shouldBe RunResult.Success

            // Verify that the configured package managers were not overwritten.
            fixtures.ortRunRepository.get(ortRun.id).shouldNotBeNull {
                resolvedJobConfigs.shouldNotBeNull {
                    analyzer.enabledPackageManagers.shouldNotBeNull() should containExactly("Gradle", "Maven")
                }
            }
        }
    }
})
