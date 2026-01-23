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

package org.eclipse.apoapsis.ortserver.config

import com.typesafe.config.Config

import org.eclipse.apoapsis.ortserver.utils.config.getConfigOrEmpty

/**
 * A test implementation of the [ConfigSecretProvider] interface. This implementation reads the known secrets from
 * the configuration passed to the factory. Thus, it can be customized for being used in tests quite easily.
 */
class ConfigSecretProviderFactoryForTesting : ConfigSecretProviderFactory {
    companion object {
        /** The name of this test implementation. */
        const val NAME = "configSecretProviderForTesting"

        /**
         * The name of a configuration property under which the test secrets are located. When an instance is created,
         * it reads this section and exposes all the properties contained as secrets.
         */
        const val SECRETS_PROPERTY = "testSecrets"
    }

    override val name: String = NAME

    override fun createProvider(config: Config): ConfigSecretProvider {
        val secrets = config.getConfigOrEmpty(SECRETS_PROPERTY)
            .entrySet().associate { it.key to it.value.unwrapped().toString() }

        return object : ConfigSecretProvider {
            override fun getSecret(path: Path): String = secrets.getValue(path.path)
        }
    }
}
