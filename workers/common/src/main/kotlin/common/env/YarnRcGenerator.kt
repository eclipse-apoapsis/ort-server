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

package org.eclipse.apoapsis.ortserver.workers.common.env

import org.eclipse.apoapsis.ortserver.workers.common.env.definition.YarnAuthMode
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.YarnDefinition

/**
 * A specialized generator class for Yarn's _.yarnrc.yml_ configuration files.
 *
 * See https://yarnpkg.com/configuration/yarnrc#npmRegistries
 */
class YarnRcGenerator : EnvironmentConfigGenerator<YarnDefinition> {
    companion object {
        /** The name of the configuration file created by this generator. */
        private const val TARGET = ".yarnrc.yml"
    }

    override val environmentDefinitionType: Class<YarnDefinition> = YarnDefinition::class.java

    override suspend fun generate(builder: ConfigFileBuilder, definitions: Collection<YarnDefinition>) {
        builder.buildInUserHome(TARGET) {
            println("npmRegistries:")
            definitions.forEachIndexed { index, definition ->
                if (index > 0) {
                    // Add an empty line between two definitions.
                    println()
                }

                println("\"${definition.service.url}\":".prependIndent(INDENT_2_SPACES))

                if (definition.alwaysAuth) { println("npmAlwaysAuth: true".prependIndent(INDENT_4_SPACES)) }

                when (definition.authMode) {
                    YarnAuthMode.AUTH_IDENT ->
                        println(
                            generateUsernamePasswordAuthentication(builder, definition)
                        )

                    YarnAuthMode.AUTH_TOKEN ->
                        println(
                            generateTokenAuthentication(builder, definition)
                        )
                }
            }
        }
    }

    private fun generateUsernamePasswordAuthentication(
        builder: ConfigFileBuilder,
        definition: YarnDefinition
    ) = (
            "npmAuthIdent: \"" +
                    "${builder.secretRef(definition.service.usernameSecret)}:" +
                    "${builder.secretRef(definition.service.passwordSecret)}\""
            )
        .prependIndent(INDENT_4_SPACES)

    private fun generateTokenAuthentication(
        builder: ConfigFileBuilder,
        definition: YarnDefinition
    ) = "npmAuthToken: \"${builder.secretRef(definition.service.passwordSecret)}\"".prependIndent(INDENT_4_SPACES)
}
