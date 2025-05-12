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

import org.eclipse.apoapsis.ortserver.workers.common.env.definition.NuGetAuthMode
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.NuGetDefinition

/**
 * A specialized generator class for NuGet's _NuGet.Config_ configuration files.
 *
 * See https://learn.microsoft.com/en-us/nuget/reference/nuget-config-file
 */
class NuGetGenerator : EnvironmentConfigGenerator<NuGetDefinition> {
    companion object {
        /** The name of the configuration file created by this generator. */
        private const val TARGET = ".nuget/NuGet/NuGet.Config"
    }

    override val environmentDefinitionType: Class<NuGetDefinition> = NuGetDefinition::class.java

    override suspend fun generate(builder: ConfigFileBuilder, definitions: Collection<NuGetDefinition>) {
        builder.buildInUserHome(TARGET) {
            println("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            println("<configuration>")

            println("<packageSources>".prependIndent(INDENT_2_SPACES))
            definitions.forEach { definition ->
                print("<add key=\"${definition.sourceName}\" ".prependIndent(INDENT_4_SPACES))
                print("value=\"${definition.sourcePath}\" ")
                if (definition.sourceProtocolVersion != null) {
                    print("protocolVersion=\"${definition.sourceProtocolVersion}\" ")
                }
                println("/>")

                GeneratorLogger.entryAdded(
                    "package source ${definition.sourceName}:${definition.sourcePath} " +
                        "protocol version: ${definition.sourceProtocolVersion.orEmpty()}",
                    TARGET,
                    definition.service
                )
            }
            println("</packageSources>".prependIndent(INDENT_2_SPACES))
            println()

            println("<packageSourceCredentials>".prependIndent(INDENT_2_SPACES))
            definitions.filter { it.authMode == NuGetAuthMode.PASSWORD }.forEach { definition ->
                println("<${definition.sourceName}>".prependIndent(INDENT_4_SPACES))
                println(generateUsernameBlock(builder, definition))
                println(generatePasswordBlock(builder, definition))
                println("</${definition.sourceName}>".prependIndent(INDENT_4_SPACES))

                GeneratorLogger.entryAdded(
                    "package credentials ${definition.sourceName}:username/password",
                    TARGET,
                    definition.service
                )
            }
            println("</packageSourceCredentials>".prependIndent(INDENT_2_SPACES))
            println()

            println("<apikeys>".prependIndent(INDENT_2_SPACES))
            definitions.filter { it.authMode == NuGetAuthMode.API_KEY }.forEach { definition ->
                println(generateApiKeyBlock(definition, builder))

                GeneratorLogger.entryAdded(
                    "auth api key ${definition.sourcePath}:apiKey",
                    TARGET,
                    definition.service
                )
            }
            println("</apikeys>".prependIndent(INDENT_2_SPACES))

            println("</configuration>")
        }
    }

    private fun generateUsernameBlock(
        builder: ConfigFileBuilder,
        definition: NuGetDefinition
    ) =
        "<add key=\"Username\" value=\"${builder.secretRef(definition.service.usernameSecret)}\" />".prependIndent(
            INDENT_6_SPACES
        )

    private fun generatePasswordBlock(
        builder: ConfigFileBuilder,
        definition: NuGetDefinition
    ) = "<add key=\"ClearTextPassword\" value=\"${builder.secretRef(definition.service.passwordSecret)}\" />"
        .prependIndent(INDENT_6_SPACES)

    private fun generateApiKeyBlock(
        definition: NuGetDefinition,
        builder: ConfigFileBuilder
    ) = "<add key=${definition.sourcePath} value=\"${builder.secretRef(definition.service.passwordSecret)}\" />"
        .prependIndent(INDENT_4_SPACES)
}
