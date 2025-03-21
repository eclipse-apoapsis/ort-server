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

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import kotlin.random.Random

import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext

import org.slf4j.LoggerFactory

/**
 * Type alias for a function that allows encoding the value of a secret before it gets inserted into a generated
 * configuration file. This is needed for instance if usernames or passwords which may contain special characters have
 * to be added to URLs.
 */
typealias SecretEncodingFun = (String) -> String

/**
 * A helper class supporting the generation of configuration files.
 *
 * This class can be used by concrete generator classes to generate package manager-specific configuration files.
 * The class exposes a [PrintWriter] for generating arbitrary content. It offers some special support for adding the
 * values of secrets to configuration files: This can be done by requesting a [secretRef] for a specific [Secret].
 * This reference is later replaced by the actual value of the secret.
 *
 * Implementation notes:
 * - This class is not thread-safe; a single instance should be used only by a single generator at a time.
 * - The implementation expects that configuration files are not big; therefore, the whole content of the file is
 *   kept in memory before it is written to disk.
 */
class ConfigFileBuilder(val context: WorkerContext) {
    companion object {
        private val logger = LoggerFactory.getLogger(ConfigFileBuilder::class.java)

        /**
         * A predefined [SecretEncodingFun] that uses the value of the secret verbatim. This is used per default and
         * does not perform any changes on the secret value.
         */
        val noEncoding: SecretEncodingFun = { it }

        /**
         * A predefined [SecretEncodingFun] that performs URL-encoding on the secret's value. This makes sure that
         * special characters contained in the secret value do not cause problems when used in the context of a URL.
         */
        val urlEncoding: SecretEncodingFun = { URLEncoder.encode(it, StandardCharsets.UTF_8) }

        /**
         * Print the given [multiLineText] making sure that the correct line endings are used. This function is
         * intended to be used with a Kotlin multiline string. In multiline strings line endings are always
         * represented by single newline characters. This function replaces this character with the platform-specific
         * newline character.
         */
        fun PrintWriter.printLines(multiLineText: String) {
            println(multiLineText.replace("\n", System.lineSeparator()))
        }

        /**
         * Execute the given [block] to generate a proxy configuration section if necessary. This function checks
         * whether a proxy is (at least partially) defined based on the typical environment variables. If this is the
         * case, the given [block] is invoked with a corresponding [ProxyConfig] and can write the specific proxy
         * configuration.
         */
        fun PrintWriter.printProxySettings(block: PrintWriter.(ProxyConfig) -> Unit) {
            val proxySettings = ProxyVariables.entries.associateWith { it.getValue() }.filterValues { it != null }

            if (proxySettings.isNotEmpty()) {
                val proxyConfig = ProxyConfig(
                    httpProxy = proxySettings[ProxyVariables.HTTP_PROXY],
                    httpsProxy = proxySettings[ProxyVariables.HTTPS_PROXY],
                    noProxy = proxySettings[ProxyVariables.NO_PROXY]
                )

                this.block(proxyConfig)
            }
        }

        /**
         * Execute the given [block] to generate a proxy configuration if necessary.
         * This variant is based on system properties that define https(s) proxy settings rather than
         * environment variables. It is typically used by tools that have a strong connection to the Java ecosystem.
         * If one of httpProxyHost or httpsProxyHost is defined, the [block] is invoked with a corresponding
         * [ProxyConfigFromSystemProperties].
         */
        fun PrintWriter.printProxySettingsFromSystemProperties(
            block: PrintWriter.(ProxyConfigFromSystemProperties) -> Unit
        ) {
            val httpProxy = ProxySystemProperties.HTTP_PROXY_HOST.getValue()?.let { host ->
                ProxySystemProperties.HTTP_PROXY_PORT.getValue()?.let { port ->
                    ProxyFromSystemProperties(host, port)
                }
            }

            val httpsProxy = ProxySystemProperties.HTTPS_PROXY_HOST.getValue()?.let { host ->
                ProxySystemProperties.HTTPS_PROXY_PORT.getValue()?.let { port ->
                    ProxyFromSystemProperties(host, port)
                }
            }

            if (httpProxy != null || httpsProxy != null) {
                block(
                    ProxyConfigFromSystemProperties(
                        httpProxy = httpProxy,
                        httpsProxy = httpsProxy,
                        nonProxyHosts = ProxySystemProperties.NON_PROXY_HOSTS.getValue()
                    )
                )
            }
        }

        /**
         * Generate a unique name for a secret reference. The name is based on a random number. Therefore, it should
         * not appear in other parts of the generated file, and no escaping needs to be implemented.
         */
        private fun generateReference(): String = "#{${Random.nextLong()}}"
    }

    /** A map storing the secret references used in the generated file. */
    private val secretReferences = mutableMapOf<String, SecretReference>()

    /**
     * Generate a configuration file at the location defined by the given [file] with content defined by the
     * given [block].
     */
    suspend fun build(file: File, block: suspend PrintWriter.() -> Unit) {
        logger.info("Generating configuration file at '{}'.", file)

        val writer = StringWriter()
        val printWriter = PrintWriter(writer)

        printWriter.block()

        val secretValues = context.resolveSecrets(*secretReferences.values.map(SecretReference::secret).toTypedArray())
        val content = secretReferences.entries.fold(writer.toString()) { text, (placeholder, reference) ->
            val encodedValue = reference.encodingFun(secretValues.getValue(reference.secret))
            text.replace(placeholder, encodedValue)
        }

        // Make sure that the parent directory exists in case the config should be stored in a subdirectory.
        if (!file.parentFile.exists()) {
            file.parentFile.mkdirs()
        }

        file.writeText(content)
    }

    /**
     * Generate a configuration in the current user's home directory with the given [name] with content defined by the
     * given [block].
     */
    suspend fun buildInUserHome(name: String, block: suspend PrintWriter.() -> Unit) {
        val file = File(System.getProperty("user.home"), name)

        build(file, block)
    }

    /**
     * Return a string-based reference to the given [secret]. This reference is replaced by the value of this
     * secret when the file is written. By specifying an [encodingFun], it is possible to apply a specific encoding on
     * the secret value before it is inserted into the file. Per default, no encoding is applied.
     */
    fun secretRef(secret: Secret, encodingFun: SecretEncodingFun = noEncoding): String {
        val ref = generateReference()
        secretReferences[ref] = SecretReference(secret, encodingFun)

        return ref
    }
}

/**
 * An enumeration class to represent the typical environment variables that define proxy settings.
 */
enum class ProxyVariables(private val variableName: String) {
    HTTP_PROXY("HTTP_PROXY"),
    HTTPS_PROXY("HTTPS_PROXY"),
    NO_PROXY("NO_PROXY");

    /**
     * Return the value of the environment variable represented by this enum constant. Return *null* if the variable
     * is not defined.
     */
    fun getValue(): String? = System.getenv(variableName) ?: System.getenv(variableName.lowercase())
}

/**
 * An enumeration class to represent the typical system properties that define proxy settings.
 */
enum class ProxySystemProperties(private val propertyName: String) {
    HTTP_PROXY_HOST("http.proxyHost"),
    HTTP_PROXY_PORT("http.proxyPort"),
    HTTPS_PROXY_HOST("https.proxyHost"),
    HTTPS_PROXY_PORT("https.proxyPort"),
    NON_PROXY_HOSTS("http.nonProxyHosts");

    fun getValue(): String? = System.getProperty(propertyName)
}

/**
 * A data class holding the single properties to define a proxy server.
 *
 * Some of the configuration files created via [ConfigFileBuilder] can contain a proxy configuration. Therefore,
 * writing such a section is supported by the builder. [ConfigFileBuilder] checks whether a proxy is configured for
 * the system based on the typical environment variables. If so, it creates an instance of this class to hold the
 * settings. Note that all properties are nullable, since a proxy configuration may be partially defined only.
 */
data class ProxyConfig(
    val httpProxy: String?,
    val httpsProxy: String?,
    val noProxy: String?
)

/**
 * A data class holding proxy information taken vom system properties.
 * In contrast to [ProxyConfig], which takes this information from environment variables, this class uses the
 * standard Java system properties like http.proxyHost to get the proxy settings.
 *
 * Note that both [httpProxy] and [httpsProxy] are nullable, since a proxy configuration may be partially defined only,
 * or there might be no proxy configuration at all.
 */
data class ProxyConfigFromSystemProperties(
    val httpProxy: ProxyFromSystemProperties?,
    val httpsProxy: ProxyFromSystemProperties?,
    val nonProxyHosts: String?
)

/**
 * A data class that defines a single proxy server with configuration taken from standard Java system properties.
 */
data class ProxyFromSystemProperties(
    val host: String,
    val port: String
)

/**
 * A data class for storing information about a [Secret] that is referenced from a configuration file. An instance
 * contains all the information required to replace the reference with the actual value of the secret.
 */
private data class SecretReference(
    /** The [Secret] that is referenced. */
    val secret: Secret,

    /** The function to encode the value of the secret. */
    val encodingFun: SecretEncodingFun
)
