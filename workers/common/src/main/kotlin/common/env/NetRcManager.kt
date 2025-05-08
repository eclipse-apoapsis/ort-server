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

import java.net.URI

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.utils.logging.runBlocking
import org.eclipse.apoapsis.ortserver.workers.common.auth.AuthenticationEvent
import org.eclipse.apoapsis.ortserver.workers.common.auth.AuthenticationListener
import org.eclipse.apoapsis.ortserver.workers.common.auth.CredentialResolverFun
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.EnvironmentServiceDefinition

import org.slf4j.LoggerFactory

/**
 * An internal helper class to dynamically update the content of the `.netrc` file when there is a change in the
 * authentication state.
 *
 * An instance of this class is created when setting up the environment for executing a worker. It is initialized with
 * the set of infrastructure services currently available. It is also registered at the central authenticator used by
 * ORT Server. Its task is to figure out, based on authentication events, which infrastructure services are currently
 * referenced. These are then written into the `.netrc` file, if they have the corresponding credentials type. This
 * should prevent conflicts with multiple services for the same host.
 */
internal class NetRcManager(
    /** The function to resolve credentials. */
    private val resolverFun: CredentialResolverFun,

    /** The currently known set of infrastructure services. */
    services: Collection<InfrastructureService>
) : AuthenticationListener {
    companion object {
        private val logger = LoggerFactory.getLogger(NetRcManager::class.java)

        /**
         * Create a new instance of [NetRcManager] and initialize it with the given [resolverFun] and [services].
         */
        fun create(resolverFun: CredentialResolverFun, services: Collection<InfrastructureService>): NetRcManager =
            NetRcManager(resolverFun, services)
    }

    /** A map with all relevant services using the service name as key. */
    private val netRcServices = services.filter { CredentialsType.NETRC_FILE in it.credentialsTypes }
        .associateBy { it.name }

    /**
     * Stores the services for which authentication events have been received. These are the services that are
     * currently referenced from the `.netrc` file. The key is the host for which a service is responsible for.
     */
    private val authenticatedServices = mutableMapOf<String, EnvironmentServiceDefinition>()

    /** A mutex to guard access to the authenticated services and updates of the `.netrc` file. */
    private val mutex = Mutex()

    override fun onAuthentication(authenticationEvent: AuthenticationEvent) {
        logger.info("Received authentication event for service '${authenticationEvent.serviceName}'.")

        netRcServices[authenticationEvent.serviceName]?.also { service ->
            runBlocking { updateNetRcServices(service) }
        }
    }

    /**
     * Return a new instance of [ConfigFileBuilder] to be used for creating the `.netrc` file.
     */
    internal fun createConfigFileBuilder(): ConfigFileBuilder = ConfigFileBuilder(resolverFun)

    /**
     * Return a new instance of [NetRcGenerator] that is going to be called to generate the `.netrc` file.
     */
    internal fun createNetRcGenerator(): NetRcGenerator = NetRcGenerator()

    /**
     * Perform an update of the `.netrc` file after receiving an authentication event for the given [service].
     */
    private suspend fun updateNetRcServices(service: InfrastructureService) {
        val serviceUri = URI.create(service.url)
        val serviceHost = serviceUri.host

        val builder = createConfigFileBuilder()
        val generator = createNetRcGenerator()

        mutex.withLock {
            if (authenticatedServices[serviceHost]?.service != service) {
                logger.info("Updating .netrc file. Adding service '${service.name}' for host '$serviceHost'.")

                authenticatedServices[serviceHost] = EnvironmentServiceDefinition(service, service.credentialsTypes)
                generator.generate(builder, authenticatedServices.values)
            }
        }
    }
}
