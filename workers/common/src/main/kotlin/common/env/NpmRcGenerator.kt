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

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

import org.eclipse.apoapsis.ortserver.workers.common.env.ConfigFileBuilder.Companion.printLines
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.NpmAuthMode
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.NpmDefinition

/**
 * A specialized generator class for NPM's _.npmrc_ configuration files.
 *
 * See https://docs.npmjs.com/cli/v9/configuring-npm/npmrc?v=true
 */
class NpmRcGenerator : EnvironmentConfigGenerator<NpmDefinition> {
    companion object {
        /** The name of the configuration file created by this generator. */
        private const val TARGET = ".npmrc"

        /** A map with proxy settings that are added to the generated file if the corresponding variables are set. */
        private val proxySettings = mapOf(
            "proxy" to "HTTP_PROXY",
            "https-proxy" to "HTTPS_PROXY",
            "noproxy" to "NO_PROXY"
        )

        /**
         * Return the value of this string base64 encoded.
         */
        @OptIn(ExperimentalEncodingApi::class)
        private fun String.base64(): String =
            Base64.encode(toByteArray())

        /**
         * Generate a configuration setting for te given [key] if an environment [variable] exists either with the
         * provided name of in lower case.
         */
        private fun settingFromEnv(key: String, variable: String): String? {
            val value = System.getenv(variable) ?: System.getenv(variable.lowercase())

            return value?.let { "$key=$it" }
        }

        /**
         * Generate proxy settings to be included in the generated configuration file based on existing environment
         * variables.
         */
        private fun generateProxySettings(): List<String> = proxySettings.mapNotNull { (key, variable) ->
            settingFromEnv(key, variable)
        }
    }

    override val environmentDefinitionType: Class<NpmDefinition>
        get() = NpmDefinition::class.java

    override suspend fun generate(builder: ConfigFileBuilder, definitions: Collection<NpmDefinition>) {
        builder.buildInUserHome(TARGET) {
            definitions.forEachIndexed { index, definition ->
                if (index > 0) {
                    // Add an empty line between two definitions.
                    println()
                }

                val serviceUri = definition.service.url.takeIf { it.endsWith('/') }
                    ?: (definition.service.url + "/")
                val uriFragment = serviceUri.substringAfter(':')
                definition.scope?.let {
                    println("@${definition.scope}:registry=$serviceUri")
                }

                printLines(generateAuthentication(builder, definition, uriFragment))

                definition.email?.let {
                    println("$uriFragment:email=$it")
                }
                if (definition.alwaysAuth) {
                    println("$uriFragment:always-auth=true")
                }
            }

            generateProxySettings().takeUnless { it.isEmpty() }?.let { proxySettings ->
                println()
                proxySettings.forEach { println(it) }
            }
        }
    }

    /**
     * Generate the part of the configuration for the given [definition] that deals with authentication using the
     * given [builder] and the given [fragment] as prefix.
     */
    private suspend fun generateAuthentication(
        builder: ConfigFileBuilder,
        definition: NpmDefinition,
        fragment: String
    ): String =
        with(definition) {
            when (authMode) {
                NpmAuthMode.PASSWORD ->
                    """
                    $fragment:username=${builder.secretRef(definition.service.usernameSecret)}
                    $fragment:_password=${builder.secretRef(definition.service.passwordSecret)}
                """.trimIndent()

                NpmAuthMode.PASSWORD_BASE64 -> {
                    val password = builder.context.resolveSecret(service.passwordSecret).base64()
                    """
                    $fragment:username=${builder.secretRef(service.usernameSecret)}
                    $fragment:_password=$password
                """.trimIndent()
                }

                NpmAuthMode.PASSWORD_AUTH ->
                    "$fragment:_auth=${builder.secretRef(service.passwordSecret)}"

                NpmAuthMode.PASSWORD_AUTH_TOKEN ->
                    "$fragment:_authToken=${builder.secretRef(service.passwordSecret)}"

                NpmAuthMode.USERNAME_PASSWORD_AUTH -> {
                    val secretValues = builder.context.resolveSecrets(
                        service.usernameSecret,
                        service.passwordSecret
                    )
                    val auth = "${secretValues[service.usernameSecret]}:${secretValues[service.passwordSecret]}"
                    "$fragment:_auth=${auth.base64()}"
                }
            }
        }
}
