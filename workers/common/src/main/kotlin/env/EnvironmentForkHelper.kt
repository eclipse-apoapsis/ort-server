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

import java.io.File
import java.io.InputStream
import java.io.OutputStream

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.model.InfrastructureServiceDeclaration
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.workers.common.ResolvedInfrastructureService
import org.eclipse.apoapsis.ortserver.workers.common.auth.AuthenticationInfo
import org.eclipse.apoapsis.ortserver.workers.common.auth.OrtServerAuthenticator
import org.eclipse.apoapsis.ortserver.workers.common.auth.infraSecretResolverFromConfig
import org.eclipse.apoapsis.ortserver.workers.common.auth.secretResolver
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerOrtConfig
import org.eclipse.apoapsis.ortserver.workers.common.enableOrtStackTraces

import org.slf4j.LoggerFactory
import org.slf4j.MDC

/**
 * An object to support with setting up a worker execution environment in a forked Java process.
 *
 * Some workers need to fork the JVM to make sure that changes on environment variables take effect. In the new
 * process, some preparations have to be done to ensure that everything works as expected. Especially, authentication
 * information has to be set up properly.
 *
 * This object provides functionality to initialize the forked process. This is a two-step process. First, relevant
 * information from the original process has to be transferred to the forked process. Then, the forked process has to
 * read this information and set up its environment accordingly. The inter-process communication is done via the
 * `stdin` stream of the forked process.
 */
@OptIn(ExperimentalSerializationApi::class)
object EnvironmentForkHelper {
    private val logger = LoggerFactory.getLogger(EnvironmentForkHelper.javaClass)

    /**
     * Prepare the data to be passed to a fork processed in order to make the worker execution environment usable.
     * Write the data to the given [stream][out]. The stream is expected to become the `stdin` of the forked process.
     */
    fun prepareFork(out: OutputStream) {
        logger.info("Preparing forked process...")

        val authInfo = fetchAuthenticationInfo()
        val mdcContext = MDC.getCopyOfContextMap().orEmpty()

        val forkData = SerializableForkData(
            authInfo = authInfo,
            mdcContext = mdcContext
        )

        Json.encodeToStream(forkData, out)

        logger.info(
            "Wrote authentication information about {} services to forked process.",
            authInfo.infosByType[OrtServerAuthenticator.PROJECT_SERVICES]?.services.orEmpty().size
        )
        logger.info("Wrote MDC context with {} entries to forked process.", mdcContext.size)
    }

    /**
     * Write the current authentication information (the set of infrastructure services with their credentials) to the
     * specified [target] file. At a later point of time, it is then possible to restore this information again.
     */
    fun persistAuthenticationInfo(target: File) {
        target.outputStream().use { out ->
            Json.encodeToStream(fetchAuthenticationInfo(), out)
        }
    }

    /**
     * Initialize the worker execution environment in a forked process using the data read from the given [pipe].
     * This function is expected to be called from the forked process using the process's `stdin` stream. It performs
     * all required actions to set up the environment for the worker execution.
     */
    fun setupFork(pipe: InputStream) {
        logger.info("Setting up forked process...")

        val forkData = Json.decodeFromStream<SerializableForkData>(pipe)
        val authInfos = forkData.authInfo
        val mdcContext = forkData.mdcContext

        restoreMdcContext(mdcContext)
        logger.info("Read MDC context with {} entries from forked process.", mdcContext.size)

        val config = WorkerOrtConfig.create()
        config.setUpOrtEnvironment()

        restoreAuthentication(authInfos, config.configManager)

        logger.info("Enabling ORT stack traces for the AnalyzerRunner forked process.")
        enableOrtStackTraces()
    }

    /**
     * Set up authentication information from the given [source] file and the provided [configManager]. Using this
     * function, the data previously stored via [persistAuthenticationInfo] can be restored.
     */
    fun setupAuthentication(source: File, configManager: ConfigManager) {
        val authInfos = source.inputStream().use { stream ->
            Json.decodeFromStream<SerializableAuthenticationInfos>(stream)
        }

        restoreAuthentication(authInfos, configManager)
    }

    /**
     * Restore the MDC context from the given [context] map.
     */
    private fun restoreMdcContext(context: Map<String, String>) =
        if (context.isEmpty()) MDC.clear() else MDC.setContextMap(context)

    /**
     * Obtain authentication information from the authenticator and return it in a form that can be serialized.
     */
    private fun fetchAuthenticationInfo(): SerializableAuthenticationInfos {
        val authenticator = OrtServerAuthenticator.install(loadEnvironmentServices = false)
        return authenticator.authenticationInfo.toSerializableAuthenticationInfos()
    }

    /**
     * Initialize the authenticator with the given deserialized [serAuthInfos] and the provided [configManager].
     */
    private fun restoreAuthentication(serAuthInfos: SerializableAuthenticationInfos, configManager: ConfigManager) {
        val authInfos = serAuthInfos.toAuthenticationInfos()
        val authenticator = OrtServerAuthenticator.install(
            infraSecretResolverFromConfig(configManager),
            loadEnvironmentServices = false
        )

        val projectAuthInfo = authInfos[OrtServerAuthenticator.PROJECT_SERVICES]
        if (projectAuthInfo != null) {
            logger.info(
                "Read authentication information about {} services from forked process.",
                projectAuthInfo.services.size
            )

            val netrcManager = NetRcManager.create(secretResolver(projectAuthInfo))
            authenticator.updateAuthenticationListener(netrcManager)
        }

        authInfos.entries.forEach { (type, authInfo) ->
            logger.info("Restoring authentication information for service type '$type'.")
            authenticator.updateAuthenticationInfo(authInfo, type)
        }
    }

    /**
     * Convert this [AuthenticationInfo] to a [SerializableAuthenticationInfo] that can be passed to a forked process.
     */
    private fun AuthenticationInfo.toSerializableAuthenticationInfo(): SerializableAuthenticationInfo =
        SerializableAuthenticationInfo(
            secrets = secrets,
            services = services.map { it.toSerializableInfrastructureService() }
        )

    /**
     * Convert this [Map] with authentication information for different types of services to a
     * [SerializableAuthenticationInfos] object that can be passed to a forked process.
     */
    private fun Map<String, AuthenticationInfo>.toSerializableAuthenticationInfos(): SerializableAuthenticationInfos =
        SerializableAuthenticationInfos(
            infosByType = mapValues { (_, authInfo) -> authInfo.toSerializableAuthenticationInfo() }
        )

    /**
     * Convert this [SerializableAuthenticationInfo] back to an [AuthenticationInfo] that can be used for
     * authentication.
     */
    private fun SerializableAuthenticationInfo.toAuthenticationInfo(): AuthenticationInfo =
        AuthenticationInfo(
            secrets = secrets,
            services = services.map { it.toInfrastructureService() }
        )

    /**
     * Convert this [SerializableAuthenticationInfos] back to a [Map] with authentication information for different
     * types of services.
     */
    private fun SerializableAuthenticationInfos.toAuthenticationInfos(): Map<String, AuthenticationInfo> =
        infosByType.mapValues { (_, authInfo) -> authInfo.toAuthenticationInfo() }

    /**
     * Convert this [ResolvedInfrastructureService] to a serializable [InfrastructureServiceDeclaration]
     */
    private fun ResolvedInfrastructureService.toSerializableInfrastructureService(): InfrastructureServiceDeclaration =
        InfrastructureServiceDeclaration(
            name = name,
            url = url,
            description = description,
            usernameSecret = usernameSecret.path,
            passwordSecret = passwordSecret.path,
            credentialsTypes = credentialsTypes
        )

    /**
     * Convert this [InfrastructureServiceDeclaration] back to an [ResolvedInfrastructureService] preserving the
     * properties required by the authentication process.
     */
    private fun InfrastructureServiceDeclaration.toInfrastructureService(): ResolvedInfrastructureService =
        ResolvedInfrastructureService(
            name = name,
            url = url,
            description = description,
            usernameSecret = createDummySecret(usernameSecret),
            passwordSecret = createDummySecret(passwordSecret),
            credentialsTypes = credentialsTypes
        )

    /**
     * Create a secret with sufficient information to be used in the authentication process for the given [path].
     */
    private fun createDummySecret(path: String): Secret =
        Secret(
            id = 0,
            path = path,
            name = path,
            description = null,
            organizationId = null,
            productId = null,
            repositoryId = null
        )
}

/**
 * A data class to store all information that needs to be handed to the forked process.
 */
@Serializable
private data class SerializableForkData(
    /** Authentication information for services. */
    val authInfo: SerializableAuthenticationInfos,

    /** The MDC context to be restored in the forked process. */
    val mdcContext: Map<String, String>
)

/**
 * A data class to store the relevant part of the authentication information that needs to be serialized to the
 * forked process. Here only the data for services is needed that is required to match their URLs and to assign the
 * correct secrets to them.
 */
@Serializable
private data class SerializableAuthenticationInfo(
    /** A map with the paths of known secrets and their values. */
    val secrets: Map<String, String>,

    /** A list with information about available infrastructure services. */
    val services: List<InfrastructureServiceDeclaration>
)

/**
 * A data class holding the authentication information for different types of services that needs to be serialized to
 * a forked process.
 */
@Serializable
private data class SerializableAuthenticationInfos(
    val infosByType: Map<String, SerializableAuthenticationInfo>
)
