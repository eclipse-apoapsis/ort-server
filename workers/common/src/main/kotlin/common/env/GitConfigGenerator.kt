/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.workers.common.env

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.utils.config.getStringOrNull
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.EnvironmentServiceDefinition

import org.slf4j.LoggerFactory

/**
 * A specialized generator class to generate a Git configuration file.
 *
 * Source code repositories may have submodules locations specified using SSH URLs. This imposes a problem, because
 * the required SSH keys to fetch the submodules are not available in the execution environment. To work around this
 * issue, Git allows to define URL `insteadOf` sections in the _.gitconfig_ file. This generator creates such
 * sections from a static configuration file, which can be customized via an environment variable.
 *
 * The `insteadOf` sections can either be generated using default values in
 * `application.conf` or customized via the environment variable
 * `GIT_CONFIG_URL_INSTEAD_OF`. This allows flexibility in defining what
 * URL `insteadOf` sections are created in `.gitconfig`.
 *
 * The expected format is a comma-separated list of base URLs and their corresponding insteadOf URLs,
 * separated by an equal sign. For example:
 * `https://github.com=ssh://git@github.com,https://github.com/=git@github.com:`
 *
 * See also: [Git documentation](https://git-scm.com/docs/git-config#Documentation/git-config.txt-urlltbasegtinsteadOf)
 *
 * There is a coupling between this generator class and the generator class for Git credentials
 * [GitCredentialsGenerator]. If Git credentials are generated, then this class also generates a `credential`
 * section in the Git configuration file _.gitconfig_ in order to reference the generated _.git-credentials_ file.
 */
class GitConfigGenerator(private val gitConfigUrlInsteadOfPairs: Map<String, String>) :
    EnvironmentConfigGenerator<EnvironmentServiceDefinition> {
    companion object {
        private const val GIT_CONFIG_FILE_NAME = ".gitconfig"

        private val logger = LoggerFactory.getLogger(GitConfigGenerator::class.java)

        private const val GIT_CONFIG_URL_INSTEAD_OF = "gitConfigUrlInsteadOf"

        /**
         * Create a new instance of [GitConfigGenerator] that already has parsed the configuration.
         */
        fun create(configManager: ConfigManager): GitConfigGenerator {
            val parsedConfiguration = configManager.config.getStringOrNull(GIT_CONFIG_URL_INSTEAD_OF)
                ?.let { configUrlInsteadOf ->
                    parseGitConfigUrlInsteadOf(configUrlInsteadOf)
                }.orEmpty()

            return GitConfigGenerator(parsedConfiguration)
        }

        internal fun parseGitConfigUrlInsteadOf(config: String) =
            config.split(",")
                .mapIndexedNotNull { index, baseInsteadOfPair ->
                    if (!baseInsteadOfPair.contains("=")) {
                        logger.warn(
                            "Invalid format of base=insteadOf pair #${index + 1}: '$baseInsteadOfPair'. " +
                                "Ignoring."
                        )
                        null
                    } else {
                        baseInsteadOfPair
                    }
                }.associate { baseInsteadOfPair ->
                    val (base, url) = baseInsteadOfPair.split("=")
                    base.trim() to url.trim()
                }

        internal suspend fun generateGitConfig(
            builder: ConfigFileBuilder,
            definitions: Collection<EnvironmentServiceDefinition>,
            gitConfigUrlInsteadOfPairs: Map<String, String>
        ) {
            val hasCredentials = definitions.any { CredentialsType.GIT_CREDENTIALS_FILE in it.credentialsTypes() }
            if (hasCredentials || gitConfigUrlInsteadOfPairs.isNotEmpty()) {
                builder.buildInUserHome(GIT_CONFIG_FILE_NAME) {
                    // If there are any Git credentials defined, create a `[credential]` section.
                    if (hasCredentials) {
                        println("[credential]")
                        println("\thelper = store")
                    }

                    // Create `url.<base>.insteadOf` sections.
                    gitConfigUrlInsteadOfPairs.forEach {
                        println("[url \"${it.key}\"]")
                        println("\tinsteadOf = \"${it.value}\"")
                    }
                }
                logger.debug(
                    "Generated .gitconfig file hasCredentials={} insteadOf={}",
                    hasCredentials,
                    gitConfigUrlInsteadOfPairs
                )
            } else {
                logger.debug("Not generating .gitconfig file.")
            }
        }
    }

    override val environmentDefinitionType: Class<EnvironmentServiceDefinition> =
        EnvironmentServiceDefinition::class.java

    override suspend fun generate(builder: ConfigFileBuilder, definitions: Collection<EnvironmentServiceDefinition>) {
        generateGitConfig(builder, definitions, gitConfigUrlInsteadOfPairs)
    }
}
