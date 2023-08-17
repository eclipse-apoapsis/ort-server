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

package org.ossreviewtoolkit.server.config.github

import com.typesafe.config.Config

import org.ossreviewtoolkit.server.config.ConfigFileProvider
import org.ossreviewtoolkit.server.config.ConfigFileProviderFactory
import org.ossreviewtoolkit.server.config.ConfigSecretProvider
import org.ossreviewtoolkit.server.config.github.GitHubConfigFileProvider.Companion.GITHUB_API_URL
import org.ossreviewtoolkit.server.config.github.GitHubConfigFileProvider.Companion.REPOSITORY_NAME

import org.slf4j.LoggerFactory

/**
 * Factory implementation for [GitHubConfigFileProvider].
 */
class GitHubConfigFileProviderFactory : ConfigFileProviderFactory {
    companion object {
        /** The name of this provider implementation. */
        const val NAME = "github-config"

        private val logger = LoggerFactory.getLogger(GitHubConfigFileProviderFactory::class.java)
    }

    override val name: String
        get() = NAME

    override fun createProvider(config: Config, secretProvider: ConfigSecretProvider): ConfigFileProvider {
        logger.info("Creating GitHubConfigFileProvider.")
        logger.debug("GitHub URI: '${config.getString(GITHUB_API_URL)}'.")
        logger.debug("GitHub Repository: '${config.getString(REPOSITORY_NAME)}'.")
        logger.debug("GitHub Repository Owner: '${config.getString(GITHUB_API_URL)}'.")

        return GitHubConfigFileProvider(config, secretProvider)
    }
}
