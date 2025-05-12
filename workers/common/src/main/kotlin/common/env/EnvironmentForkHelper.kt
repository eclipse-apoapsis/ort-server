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

import java.io.InputStream
import java.io.OutputStream

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.InfrastructureServiceDeclaration
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.workers.common.auth.AuthenticationInfo
import org.eclipse.apoapsis.ortserver.workers.common.auth.OrtServerAuthenticator
import org.eclipse.apoapsis.ortserver.workers.common.auth.credentialResolver

import org.slf4j.LoggerFactory

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
object EnvironmentForkHelper {
    private val logger = LoggerFactory.getLogger(EnvironmentForkHelper.javaClass)

    /**
     * Prepare the data to be passed to a fork processed in order to make the worker execution environment usable.
     * Write the data to the given [stream][out]. The stream is expected to become the `stdin` of the forked process.
     */
    fun prepareFork(out: OutputStream) {
        logger.info("Preparing forked process...")

        val authenticator = OrtServerAuthenticator.install()
        val authInfo = authenticator.authenticationInfo

        Json.encodeToStream(authInfo.toSerializableAuthenticationInfo(), out)

        logger.info("Wrote authentication information about {} services to forked process.", authInfo.services.size)
    }

    /**
     * Initialize the worker execution environment in a forked process using the data read from the given [pipe].
     * This function is expected to be called from the forked process using the process's `stdin` stream. It performs
     * all required actions to set up the environment for the worker execution.
     */
    fun setupFork(pipe: InputStream) {
        logger.info("Setting up forked process...")

        val authInfo = Json.decodeFromStream<SerializableAuthenticationInfo>(pipe).toAuthenticationInfo()

        logger.info("Read authentication information about {} services from forked process.", authInfo.services.size)

        val authenticator = OrtServerAuthenticator.install()
        authenticator.updateAuthenticationInfo(authInfo)

        val netrcManager = NetRcManager.create(credentialResolver(authInfo))
        authenticator.updateAuthenticationListener(netrcManager)
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
     * Convert this [SerializableAuthenticationInfo] back to an [AuthenticationInfo] that can be used for
     * authentication.
     */
    private fun SerializableAuthenticationInfo.toAuthenticationInfo(): AuthenticationInfo =
        AuthenticationInfo(
            secrets = secrets,
            services = services.map { it.toInfrastructureService() }
        )

    /**
     * Convert this [InfrastructureService] to a serializable [InfrastructureServiceDeclaration]
     */
    private fun InfrastructureService.toSerializableInfrastructureService(): InfrastructureServiceDeclaration =
        InfrastructureServiceDeclaration(
            name = name,
            url = url,
            description = description,
            usernameSecret = usernameSecret.path,
            passwordSecret = passwordSecret.path,
            credentialsTypes = credentialsTypes
        )

    /**
     * Convert this [InfrastructureServiceDeclaration] back to an [InfrastructureService] preserving the properties
     * required by the authentication process.
     */
    private fun InfrastructureServiceDeclaration.toInfrastructureService(): InfrastructureService =
        InfrastructureService(
            name = name,
            url = url,
            description = description,
            usernameSecret = createDummySecret(usernameSecret),
            passwordSecret = createDummySecret(passwordSecret),
            credentialsTypes = credentialsTypes,
            organization = null,
            product = null
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
            organization = null,
            product = null,
            repository = null
        )
}

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
