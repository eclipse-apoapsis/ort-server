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

/**
 * Factory implementation for [GitHubConfigFileProvider].
 */
class GitHubConfigFileProviderFactory : ConfigFileProviderFactory {
    companion object {
        /** The name of this provider implementation. */
        const val NAME = "github-config"
    }

    override val name: String
        get() = NAME

    override fun createProvider(config: Config, secretProvider: ConfigSecretProvider): ConfigFileProvider =
        GitHubConfigFileProvider.create(config, secretProvider)
}
