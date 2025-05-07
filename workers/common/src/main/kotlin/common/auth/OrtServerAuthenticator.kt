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

package org.eclipse.apoapsis.ortserver.workers.common.auth

import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.URI
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

import org.ossreviewtoolkit.utils.ort.OrtAuthenticator
import org.ossreviewtoolkit.utils.ort.UserInfoAuthenticator

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(OrtServerAuthenticator::class.java)

/**
 * Implementation of an [Authenticator] which is responsible for handling authentication within ORT Server.
 *
 * This implementation uses special authentication logic based on infrastructure services and their associated
 * credentials. It also uses functionality from ORT's authentication mechanism, but makes sure that the different
 * sources of authentication information are queried in the correct order:
 * - If the URL contains credentials, these are used first.
 * - If there was already an [Authenticator] installed when this instance was created, it is invoked next. This allows
 *   overriding the default authentication mechanism temporarily.
 * - If the former steps did not yield a result, the class obtains the credentials from the best-matching
 *   infrastructure service if any.
 *
 * When setting up a new worker context, this authenticator is installed as the default authenticator. It is
 * uninstalled when the context is closed. Since the set of infrastructure services can change during the execution
 * time of a worker (for instance after parsing the `.env.ort.yml` file), this implementation can be modified
 * dynamically. Workers do not interact with this class directly, but use functionality provided by the
 * worker context interface instead.
 */
internal class OrtServerAuthenticator(
    /** The original authenticator that was active when this instance was installed. */
    original: Authenticator? = null
) : OrtAuthenticator(original) {
    companion object {
        /**
         * Install this authenticator as the global default if it is not already installed.
         */
        @Synchronized
        fun install(): OrtServerAuthenticator {
            val active = getDefault()
            return active as? OrtServerAuthenticator
                ?: OrtServerAuthenticator(active).also {
                    setDefault(it)
                    logger.info("OrtServerAuthenticator was successfully installed.")
                }
        }
    }

    /**
     * A reference to the current set of services for which authentication information is available. It is not
     * expected that the services are updated concurrently, but they may be accessed from different threads.
     * Therefore, an atomic reference is used to ensure safe publishing of changes.
     */
    private val refServices = AtomicReference(ServiceData(emptyMap()))

    /**
     * A reference to the listener to be notified about successful authentications. This listener can be set
     * dynamically when setting up the environment for a worker.
     */
    private val refListener = AtomicReference<AuthenticationListener>()

    override val delegateAuthenticators: List<Authenticator> = listOfNotNull(
        UserInfoAuthenticator(),
        original,
        ServicesAuthenticator(refServices, refListener)
    )

    /**
     * Update the list of [services] for which authentication information is available. The authenticator is able to
     * handle password requests for these services.
     */
    fun updateAuthenticatedServices(services: Collection<AuthenticatedService>) {
        logger.info("Updating the list of authenticated services. Setting ${services.size} services.")

        val validatedServices = services.mapNotNull { service ->
            runCatching {
                URI.create(service.uri) to service
            }.onFailure {
                logger.error("Invalid URI for service '${service.name}': '${service.uri}'. Ignoring service.", it)
            }.getOrNull()
        }.groupBy { it.first.host }
            .mapValues { e -> e.value.map { it.second.withTrailingSlash() } }

        refServices.set(ServiceData(validatedServices))
    }

    /**
     * Set the [listener] to be notified on successful authentications. Note that for the use cases of this class,
     * only a single listener is needed; therefore, there is no `add` method.
     */
    fun updateAuthenticationListener(listener: AuthenticationListener?) {
        logger.info("Updating the authentication listener.")
        refListener.set(listener)
    }
}

/**
 * An internally used data class to store information about the services that can be authenticated.
 */
private data class ServiceData(
    /** A [Map] storing the known services grouped by their host name. */
    private val servicesByHost: Map<String, Collection<AuthenticatedService>>
) {
    /**
     * Find the best-matching [AuthenticatedService] for the given [host] and optional [url]. If there are multiple
     * services for the same host, the one with the longest matching URL is returned. Returns `null` if no
     * matching service is found.
     */
    fun getAuthenticatedService(host: String, url: URL?): AuthenticatedService? {
        val services = servicesByHost[url?.host ?: host].orEmpty()

        val matchingServices = url?.let { requestUrl ->
            val strUrl = "${requestUrl.toString().removeSuffix("/")}/"
            services.filter { strUrl.startsWith(it.uri) || it.uri == strUrl }
        } ?: services

        return matchingServices.maxByOrNull { it.uri.length }
    }
}

/**
 * Implementation of an [Authenticator] which uses the current set of infrastructure services to authenticate requests.
 */
private class ServicesAuthenticator(
    /** A reference to the set of services for which authentication information is available. */
    private val refServices: AtomicReference<ServiceData>,

    /** A reference to a listener to be notified on successful authentications. */
    private val authenticationListener: AtomicReference<AuthenticationListener>
) : Authenticator() {
    override fun getPasswordAuthentication(): PasswordAuthentication? {
        if (requestorType != RequestorType.SERVER) return null

        logger.info("Request for password authentication for '${requestingURL ?: requestingHost}'.")

        return refServices.get().getAuthenticatedService(requestingHost, requestingURL)?.let { service ->
            logger.info("Using credentials from service '${service.name}'.")

            authenticationListener.get()?.also { listener ->
                listener.onAuthentication(AuthenticationEvent(service.name))
            }

            PasswordAuthentication(service.username, service.password.toCharArray())
        }
    }
}

/**
 * Return an [AuthenticatedService] instance whose URI is guaranteed to end on a slash. This is needed for correct
 * prefix matching.
 */
private fun AuthenticatedService.withTrailingSlash(): AuthenticatedService =
    this.takeIf { uri.endsWith('/') } ?: copy(uri = "$uri/")
