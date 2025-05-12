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

import org.eclipse.apoapsis.ortserver.workers.common.env.definition.ConanDefinition

/**
 * A specialized generator class for Conan's _remotes.json_ configuration files.
 *
 * See: https://docs.conan.io/2.0/reference/config_files/remotes.html
 */
class ConanGenerator : EnvironmentConfigGenerator<ConanDefinition> {
    companion object {
        /** The name of the configuration file created by this generator. */
        private const val TARGET = ".conan/remotes.json"
    }

    override val environmentDefinitionType: Class<ConanDefinition> = ConanDefinition::class.java

    override suspend fun generate(builder: ConfigFileBuilder, definitions: Collection<ConanDefinition>) {
        builder.buildInUserHome(TARGET) {
            println("{")
            println("\"remotes\": [".prependIndent(INDENT_2_SPACES))
            definitions.forEachIndexed { index, definition ->
                if (index > 0) {
                    // Print a comma and a line break before each definition for multi-definition remote.json files
                    println(",")
                }

                println("{".prependIndent(INDENT_4_SPACES))

                println("\"name\": \"${definition.name}\",".prependIndent(INDENT_6_SPACES))
                println("\"url\": \"${definition.remoteUrl}\",".prependIndent(INDENT_6_SPACES))
                println("\"verify_ssl\": ${definition.verifySsl}".prependIndent(INDENT_6_SPACES))

                print("}".prependIndent(INDENT_4_SPACES))
            }
            println()
            println("]".prependIndent(INDENT_2_SPACES))
            println("}")
        }
    }
}
