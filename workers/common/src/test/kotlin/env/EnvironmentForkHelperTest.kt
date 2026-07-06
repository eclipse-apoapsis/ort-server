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

import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.workers.common.ResolvedInfrastructureService
import org.eclipse.apoapsis.ortserver.workers.common.auth.AuthenticationEvent
import org.eclipse.apoapsis.ortserver.workers.common.auth.AuthenticationInfo
import org.eclipse.apoapsis.ortserver.workers.common.auth.AuthenticationListener
import org.eclipse.apoapsis.ortserver.workers.common.auth.InfraSecretResolverFun
import org.eclipse.apoapsis.ortserver.workers.common.auth.OrtServerAuthenticator
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerOrtConfig

import org.ossreviewtoolkit.utils.common.Os

import org.slf4j.MDC

class EnvironmentForkHelperTest : StringSpec({
    beforeEach {
        mockkObject(OrtServerAuthenticator)
    }

    afterEach {
        unmockkAll()
        MDC.clear()
    }

    "Authentication information should be correctly initialized" {
        val usernameSecret = createSecret("user")
        val repoPasswordSecret = createSecret("repoPassword")
        val artifactoryPasswordSecret = createSecret("artifactoryPassword")
        val repoService = createService("repo", usernameSecret, repoPasswordSecret)
        val artifactoryService = createService("artifactory", usernameSecret, artifactoryPasswordSecret)
        val secrets = mapOf(
            usernameSecret.toAuthenticationInfo(),
            repoPasswordSecret.toAuthenticationInfo()
        )
        val authInfo1 = AuthenticationInfo(
            secrets = secrets,
            services = listOf(repoService)
        )
        val authInfo2 = AuthenticationInfo(
            secrets = mapOf(artifactoryPasswordSecret.toAuthenticationInfo()),
            services = listOf(artifactoryService)
        )
        val authInfoMap = mapOf("source1" to authInfo1, "source2" to authInfo2)

        val authenticator = installAuthenticatorMock(authInfoMap)

        val config = doFork()
        val testSecret = Path("testSecret")
        val testSecretValue = "value of test secret"
        val configManager = config.configManager
        every { configManager.getSecret(testSecret) } returns testSecretValue

        verify {
            config.setUpOrtEnvironment()
        }

        val slotResolver = mutableListOf<InfraSecretResolverFun>()
        verify(exactly = 2) {
            OrtServerAuthenticator.install(capture(slotResolver), any())
        }
        slotResolver.last().invoke(testSecret) shouldBe testSecretValue

        val slotAuthInfo1 = slot<AuthenticationInfo>()
        val slotAuthInfo2 = slot<AuthenticationInfo>()
        verify {
            authenticator.updateAuthenticationInfo(capture(slotAuthInfo1), "source1")
            authenticator.updateAuthenticationInfo(capture(slotAuthInfo2), "source2")
        }

        with(slotAuthInfo1.captured) {
            resolveSecret(usernameSecret) shouldBe secretValue(usernameSecret.name)
            resolveSecret(repoPasswordSecret) shouldBe secretValue(repoPasswordSecret.name)
        }
        with(slotAuthInfo2.captured) {
            resolveSecret(artifactoryPasswordSecret) shouldBe secretValue(artifactoryPasswordSecret.name)
        }
    }

    "A correct authentication listener should be registered" {
        val usernameSecret = createSecret("user")
        val passwordSecret1 = createSecret("pwd1")
        val passwordSecret2 = createSecret("pwd2")
        val service1 = createService("repo1", usernameSecret, passwordSecret1)
        val service2 = createService(
            "repo2",
            usernameSecret,
            passwordSecret2,
            credentialsTypes = setOf(CredentialsType.NETRC_FILE)
        )
        val secrets = mapOf(
            usernameSecret.toAuthenticationInfo(),
            passwordSecret1.toAuthenticationInfo(),
            passwordSecret2.toAuthenticationInfo()
        )
        val authInfo = AuthenticationInfo(
            secrets = secrets,
            services = listOf(service1, service2)
        )

        val authenticator = installAuthenticatorMock(mapOf(OrtServerAuthenticator.PROJECT_SERVICES to authInfo))

        val userHomeDir = tempdir()
        mockkObject(Os) {
            every { Os.userHomeDirectory } returns userHomeDir

            doFork()

            val slotListener = slot<AuthenticationListener>()
            verify {
                authenticator.updateAuthenticationListener(capture(slotListener))
            }

            with(slotListener.captured) {
                onAuthentication(AuthenticationEvent(service1))
                onAuthentication(AuthenticationEvent(service2))
            }
        }

        val netrcFile = userHomeDir.resolve(".netrc")
        netrcFile.isFile shouldBe true

        val netrcText = netrcFile.readText()
        netrcText shouldContain "machine ${service2.host()}"
        netrcText shouldContain secretValue(passwordSecret2.name)
        netrcText shouldNotContain service1.host()
    }

    "MDC context should be correctly transferred and restored in forked process" {
        val originalMdcContext = mapOf(
            "traceId" to "test-trace-123",
            "ortRunId" to "456",
            "analyzerJobId" to "789",
            "component" to "analyzer-worker"
        )

        originalMdcContext.forEach { (key, value) -> MDC.put(key, value) }

        // Simulate the fork process
        val processInput = ByteArrayOutputStream()
        EnvironmentForkHelper.prepareFork(processInput)

        // Clear MDC to simulate fresh forked process
        MDC.clear()
        MDC.getCopyOfContextMap() should beNull()

        // Simulate setupFork in the forked process
        val stdin = ByteArrayInputStream(processInput.toByteArray())
        EnvironmentForkHelper.setupFork(stdin)

        val restoredContext = MDC.getCopyOfContextMap()
        restoredContext shouldBe originalMdcContext

        MDC.get("traceId") shouldBe "test-trace-123"
        MDC.get("ortRunId") shouldBe "456"
        MDC.get("analyzerJobId") shouldBe "789"
        MDC.get("component") shouldBe "analyzer-worker"
    }

    "Authentication information should be correctly persisted and restored" {
        val usernameSecret = createSecret("user")
        val repoPasswordSecret = createSecret("repoPassword")
        val artifactoryPasswordSecret = createSecret("artifactoryPassword")
        val repoService = createService("repo", usernameSecret, repoPasswordSecret)
        val artifactoryService = createService("artifactory", usernameSecret, artifactoryPasswordSecret)
        val secrets = mapOf(
            usernameSecret.toAuthenticationInfo(),
            repoPasswordSecret.toAuthenticationInfo()
        )
        val authInfo1 = AuthenticationInfo(
            secrets = secrets,
            services = listOf(repoService)
        )
        val authInfo2 = AuthenticationInfo(
            secrets = mapOf(artifactoryPasswordSecret.toAuthenticationInfo()),
            services = listOf(artifactoryService)
        )
        val authInfoMap = mapOf("source1" to authInfo1, "source2" to authInfo2)
        val authenticator = installAuthenticatorMock(authInfoMap)
        val testSecret = Path("testSecret")
        val testSecretValue = "value of test secret"
        val configManager = mockk<ConfigManager>()
        every { configManager.getSecret(testSecret) } returns testSecretValue

        val file = tempfile()
        EnvironmentForkHelper.persistAuthenticationInfo(file)
        EnvironmentForkHelper.setupAuthentication(file, configManager)

        val slotResolver = mutableListOf<InfraSecretResolverFun>()
        verify(exactly = 2) {
            OrtServerAuthenticator.install(capture(slotResolver), any())
        }
        slotResolver.last().invoke(testSecret) shouldBe testSecretValue

        val slotAuthInfo1 = slot<AuthenticationInfo>()
        val slotAuthInfo2 = slot<AuthenticationInfo>()
        verify {
            authenticator.updateAuthenticationInfo(capture(slotAuthInfo1), "source1")
            authenticator.updateAuthenticationInfo(capture(slotAuthInfo2), "source2")
        }

        with(slotAuthInfo1.captured) {
            resolveSecret(usernameSecret) shouldBe secretValue(usernameSecret.name)
            resolveSecret(repoPasswordSecret) shouldBe secretValue(repoPasswordSecret.name)
        }
        with(slotAuthInfo2.captured) {
            resolveSecret(artifactoryPasswordSecret) shouldBe secretValue(artifactoryPasswordSecret.name)
        }
    }
})

/**
 * Simulate a fork operation by transferring the relevant information to the simulated child process. Return a mock
 * for the [WorkerOrtConfig] which is used in the forked process.
 */
private fun doFork(): WorkerOrtConfig {
    val mockConfig = mockk<WorkerOrtConfig> {
        every { setUpOrtEnvironment() } just runs
        every { configManager } returns mockk()
    }
    mockkObject(WorkerOrtConfig)
    every { WorkerOrtConfig.create() } returns mockConfig

    val processInput = ByteArrayOutputStream()
    EnvironmentForkHelper.prepareFork(processInput)

    val stdin = ByteArrayInputStream(processInput.toByteArray())
    EnvironmentForkHelper.setupFork(stdin)

    return mockConfig
}

/**
 * Create a mock [OrtServerAuthenticator] and prepare the installation function to return it. Configure the mock to
 * expect some interactions and return the given [authInfo] when asked for it.
 */
private fun installAuthenticatorMock(authInfo: Map<String, AuthenticationInfo>): OrtServerAuthenticator =
    mockk<OrtServerAuthenticator> {
        every { authenticationInfo } returns authInfo
        every { updateAuthenticationInfo(any(), any()) } just runs
        every { updateAuthenticationListener(any()) } just runs
    }.also {
        every { OrtServerAuthenticator.install(any(), false) } returns it
    }

/**
 * Generate a path for a [Secret] with the given [name].
 */
private fun secretPath(name: String): String = "secret/$name"

/**
 * Generate a value for the test [Secret] with the given [name].
 */
private fun secretValue(name: String): String = "secret value for $name"

/**
 * Return a [Pair] with information about this [Secret] and its value that can be used in authentication information.
 */
private fun Secret.toAuthenticationInfo(): Pair<String, String> = path to secretValue(name)

/**
 * Create a test [Secret] with the given name and some default properties.
 */
private fun createSecret(name: String): Secret =
    Secret(
        id = 0,
        path = secretPath(name),
        name = name,
        description = "description for $name",
        organizationId = null,
        productId = null,
        repositoryId = null
    )

/**
 * Create a test [ResolvedInfrastructureService] with the given properties.
 */
private fun createService(
    name: String,
    userSecret: Secret,
    passwordSecret: Secret,
    credentialsTypes: Set<CredentialsType> = emptySet()
): ResolvedInfrastructureService =
    ResolvedInfrastructureService(
        name = name,
        url = "https://$name.example.com/service",
        usernameSecret = userSecret,
        passwordSecret = passwordSecret,
        credentialsTypes = credentialsTypes
    )

/**
 * Return the host name of this [InfrastructureService]'s URL.
 */
private fun ResolvedInfrastructureService.host(): String = URI.create(url).host
