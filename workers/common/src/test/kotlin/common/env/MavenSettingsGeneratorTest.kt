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

import io.kotest.core.spec.style.WordSpec
import io.kotest.extensions.system.OverrideMode
import io.kotest.extensions.system.withSystemProperties
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith

import io.mockk.every

import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.services.config.MavenCentralMirror
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.MavenDefinition

class MavenSettingsGeneratorTest : WordSpec({
    "environmentDefinitionType" should {
        "return the correct definition class" {
            val definitionType = MavenSettingsGenerator().environmentDefinitionType

            definitionType shouldBe MavenDefinition::class.java
        }
    }

    "generate" should {
        "generate the file at the correct location" {
            val definition = MavenDefinition(
                MockConfigFileBuilder.createInfrastructureService(
                    "https://repo.example.org/test.git",
                    MockConfigFileBuilder.createSecret("sec1"),
                    MockConfigFileBuilder.createSecret("sec2")
                ),
                emptySet(),
                "repo"
            )

            val mockBuilder = MockConfigFileBuilder()

            MavenSettingsGenerator().generate(mockBuilder.builder, listOf(definition))

            mockBuilder.homeFileName shouldBe ".m2/settings.xml"
        }

        "generate correct blocks for the referenced services" {
            val secUser1 = MockConfigFileBuilder.createSecret("user1Secret")
            val secPass1 = MockConfigFileBuilder.createSecret("pass1Secret")
            val secUser2 = MockConfigFileBuilder.createSecret("user2Secret")
            val secPass2 = MockConfigFileBuilder.createSecret("pass2Secret")

            val definition1 = MavenDefinition(
                MockConfigFileBuilder.createInfrastructureService("https://repo1.example.org", secUser1, secPass1),
                emptySet(),
                "repo1"
            )
            val definition2 = MavenDefinition(
                MockConfigFileBuilder.createInfrastructureService("https://repo2.example.org", secUser2, secPass2),
                emptySet(),
                "repo2"
            )
            val definition3 = MavenDefinition(
                MockConfigFileBuilder.createInfrastructureService("https://repo3.example.org", secUser2, secPass2),
                emptySet(),
                "repo3"
            )

            val mockBuilder = MockConfigFileBuilder()

            MavenSettingsGenerator().generate(mockBuilder.builder, listOf(definition1, definition2, definition3))

            val content = mockBuilder.generatedText()
            content.shouldContain(serverBlock("repo1", secUser1, secPass1).withIndent(8))
            content.shouldContain(serverBlock("repo2", secUser2, secPass2).withIndent(8))
            content.shouldContain(serverBlock("repo3", secUser2, secPass2).withIndent(8))
        }

        "generate a correct the correct structure of the settings.xml file" {
            val definition = MavenDefinition(
                MockConfigFileBuilder.createInfrastructureService(
                    "https://repo.example.org/test.git",
                    MockConfigFileBuilder.createSecret("sec1"),
                    MockConfigFileBuilder.createSecret("sec2")
                ),
                emptySet(),
                "repo"
            )

            val mockBuilder = MockConfigFileBuilder()

            MavenSettingsGenerator().generate(mockBuilder.builder, listOf(definition))

            val lines = mockBuilder.generatedLines()
            lines[0] shouldStartWith "<settings "
            lines[0] shouldContain "xmlns=\"http://maven.apache.org/SETTINGS/1.1.0\""
            lines[0] shouldContain "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
            lines[0] shouldContain "xsi:schemaLocation=\"http://maven.apache.org/SETTINGS/1.1.0 " +
                    "http://maven.apache.org/xsd/settings-1.1.0.xsd\""
            lines[1] shouldBe "    <servers>"
            lines[2] shouldBe "        <server>"
            lines[3] shouldBe "            <id>repo</id>"
            lines[lines.size - 2] shouldBe "    </servers>"
            lines[lines.size - 1] shouldBe "</settings>"
        }

        "generates no proxy section if system properties are not set" {
            withSystemProperties(
                mapOf(
                    "http.proxyHost" to null,
                    "https.proxyPort" to null
                ),
                OverrideMode.SetOrOverride
            ) {
                val mockBuilder = MockConfigFileBuilder()
                MavenSettingsGenerator().generate(mockBuilder.builder, emptyList())

                mockBuilder.generatedText() shouldNotContain "<proxies>"
                mockBuilder.generatedText() shouldNotContain "</proxies>"
            }
        }

        "generates a proxy section if only http proxy is defined in system properties" {
            // Only http proxy defined in system properties
            withSystemProperties(
                mapOf(
                    "http.proxyHost" to "http-proxy.example.com",
                    "http.proxyPort" to "8080",
                    "https.proxyHost" to null,
                    "https.proxyPort" to null,
                    "http.nonProxyHosts" to "*.example.com|*.cluster.local"
                ),
                OverrideMode.SetOrOverride
            ) {
                val mockBuilder = MockConfigFileBuilder()
                MavenSettingsGenerator().generate(mockBuilder.builder, emptyList())

                val regexHttpProxyDefinition = "<proxies>\\s*" +
                        "<proxy>\\s*" +
                        "<id>.*?</id>\\s*" +
                        "<active>true</active>\\s*" +
                        "<protocol>http</protocol>\\s*" +
                        "<host>http-proxy.example.com</host>\\s*" +
                        "<port>8080</port>\\s*" +
                        "<nonProxyHosts>\\*.example.com\\|\\*.cluster.local</nonProxyHosts>\\s*" +
                        "</proxy>\\s*" +
                        "</proxies>"

                mockBuilder.generatedText() shouldContain (
                    Regex(
                        regexHttpProxyDefinition,
                        setOf(RegexOption.DOT_MATCHES_ALL)
                    )
                )
            }
        }

        "generates a proxy section if only https proxy is defined in system properties" {
            // Only https proxy defined in system properties
            withSystemProperties(
                mapOf(
                    "http.proxyHost" to null,
                    "http.proxyPort" to null,
                    "https.proxyHost" to "https-proxy.example.com",
                    "https.proxyPort" to "8081",
                    "http.nonProxyHosts" to "*.example.com|*.cluster.local"
                ),
                OverrideMode.SetOrOverride
            ) {
                val mockBuilder = MockConfigFileBuilder()
                MavenSettingsGenerator().generate(mockBuilder.builder, emptyList())

                val regexHttpProxyDefinition = "<proxies>\\s*" +
                        "<proxy>\\s*" +
                        "<id>.*?</id>\\s*" +
                        "<active>true</active>\\s*" +
                        "<protocol>https</protocol>\\s*" +
                        "<host>https-proxy.example.com</host>\\s*" +
                        "<port>8081</port>\\s*" +
                        "<nonProxyHosts>\\*.example.com\\|\\*.cluster.local</nonProxyHosts>\\s*" +
                        "</proxy>\\s*" +
                        "</proxies>"

                mockBuilder.generatedText() shouldContain (
                    Regex(
                        regexHttpProxyDefinition,
                        setOf(RegexOption.DOT_MATCHES_ALL)
                    )
                )
            }
        }

        "generates a proxy section for both http and https if defined in system properties" {
            // Both http and https proxy defined in system properties
            withSystemProperties(
                mapOf(
                    "http.proxyHost" to "http-proxy.example.com",
                    "http.proxyPort" to "8080",
                    "https.proxyHost" to "https-proxy.example.com",
                    "https.proxyPort" to "8081",
                    "http.nonProxyHosts" to "*.example.com|*.cluster.local"
                ),
                OverrideMode.SetOrOverride
            ) {
                val mockBuilder = MockConfigFileBuilder()
                MavenSettingsGenerator().generate(mockBuilder.builder, emptyList())

                // Just make sure that there are two <proxy> sections, one for http and one for https.
                // Details of the <proxy> sections are covered already by other tests.
                val regexHttpProxyDefinition = "<proxies>\\s*" +
                        "<proxy>.*" +
                        "<protocol>http</protocol>.*" +
                        "</proxy>\\s*" +
                        "<proxy>.*" +
                        "<protocol>https</protocol>.*" +
                        "</proxy>\\s*" +
                        "</proxies>"

                mockBuilder.generatedText() shouldContain (
                    Regex(
                        regexHttpProxyDefinition,
                        setOf(RegexOption.DOT_MATCHES_ALL)
                    )
                )
            }
        }

        "not generate mirrors section if MavenCentralMirror is null" {
            val mockBuilder = MockConfigFileBuilder()

            MavenSettingsGenerator().generate(mockBuilder.builder, emptyList())

            val content = mockBuilder.generatedText()
            content shouldNotContain "<mirrors>"
            content shouldNotContain "</mirrors>"
        }

        "generate a mirror block without a server block if the MavenCentralMirror has no credentials" {
            val mavenCentralMirror = MavenCentralMirror(
                id = "central",
                name = "Maven Central",
                url = "https://repo.maven.apache.org/maven2",
                mirrorOf = "central"
            )

            val mockBuilder = MockConfigFileBuilder()
            every { mockBuilder.adminConfig.mavenCentralMirror } returns mavenCentralMirror

            MavenSettingsGenerator().generate(mockBuilder.builder, emptyList())

            val content = mockBuilder.generatedText()
            content.shouldContain(
                mirrorBlock(
                    mavenCentralMirror.id,
                    mavenCentralMirror.name,
                    mavenCentralMirror.url,
                    mavenCentralMirror.mirrorOf
                ).withIndent(8)
            )
        }

        "generate both server and mirror block if MavenCentralMirror has credentials" {
            val username = "test-username"
            val infraUsernameSecret = MockConfigFileBuilder.createSecret("infra-secret-username")
            val password = "test-password"
            val infraPasswordSecret = MockConfigFileBuilder.createSecret("infra-secret-password")
            val mavenCentralMirror = MavenCentralMirror(
                id = "central",
                name = "Maven Central",
                url = "https://repo.maven.apache.org/maven2",
                mirrorOf = "central",
                usernameSecret = infraUsernameSecret.name,
                passwordSecret = infraPasswordSecret.name
            )

            val mockBuilder = MockConfigFileBuilder()
            every { mockBuilder.infraSecretResolverFun.invoke(Path(infraUsernameSecret.path)) } returns username
            every { mockBuilder.infraSecretResolverFun.invoke(Path(infraPasswordSecret.path)) } returns password
            every { mockBuilder.adminConfig.mavenCentralMirror } returns mavenCentralMirror

            MavenSettingsGenerator().generate(mockBuilder.builder, emptyList())

            val content = mockBuilder.generatedText()

            content.shouldContain(
                mirrorBlock(
                    mavenCentralMirror.id,
                    mavenCentralMirror.name,
                    mavenCentralMirror.url,
                    mavenCentralMirror.mirrorOf
                ).withIndent(8)
            )

            content.shouldContain(
                serverBlock(
                    "central",
                    infraUsernameSecret,
                    infraPasswordSecret,
                    { secret -> mockBuilder.infraSecretResolverFun.invoke(Path(secret.path)) }
                ).withIndent(8)
            )
        }
    }
})

/**
 * Generate a block that defines a server in a _settings.xml_ file based on the given [id], [username], and
 * [password]. For resolving the secrets, the [secretResolver] function can be provided, which defaults to
 * [MockConfigFileBuilder.testSecretRef].
 */
private fun serverBlock(
    id: String,
    username: Secret,
    password: Secret,
    secretResolver: (Secret) -> String = MockConfigFileBuilder::testSecretRef
): String =
    """
        |<server>
        |    <id>$id</id>
        |    <username>${secretResolver(username)}</username>
        |    <password>${secretResolver(password)}</password>
        |</server>
    """.trimMargin()

/**
 * Generates a block that defines a mirror in a _settings.xml_ file based on the given [id], [name], [url], and
 * [mirrorOf].
 */
private fun mirrorBlock(id: String, name: String, url: String, mirrorOf: String): String =
    """
        |<mirror>
        |    <id>$id</id>
        |    <name>$name</name>
        |    <url>$url</url>
        |    <mirrorOf>$mirrorOf</mirrorOf>
        |</mirror>
    """.trimMargin()

/**
 * Generate a string with all lines indented by the given [indent].
 */
private fun String.withIndent(indent: Int): String {
    val indentStr = " ".repeat(indent)
    return lineSequence().map { "$indentStr$it" }.joinToString(System.lineSeparator())
}
