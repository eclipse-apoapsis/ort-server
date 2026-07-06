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
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicReference

import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.workers.common.ResolvedInfrastructureService

import org.ossreviewtoolkit.utils.authentication.OrtAuthenticator
import org.ossreviewtoolkit.utils.authentication.UserInfoAuthenticator

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(OrtServerAuthenticator::class.java)

/**
 * Implementation of an [Authenticator] which is responsible for handling authentication within ORT Server. This class
 * supports both authentication based on credentials managed on behalf of end users, and authentication based on
 * secrets obtained via the configuration. The latter is done by using URLs that have a user info component; however,
 * the credentials contained here are not used directly, but resolved against the secrets provider from the
 * configuration. This makes it easy to specify the URLs of external systems in the configuration and map them to
 * credentials managed by a secrets storage.
 *
 * This implementation uses special authentication logic based on infrastructure services and their associated
 * credentials. It also uses functionality from ORT's authentication mechanism, but makes sure that the different
 * sources of authentication information are queried in the correct order:
 * - If the URL contains credentials, these are used first after attempting to resolve them.
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
        /** Constant for the default type of services, which are services defined by the projects to be analyzed. */
        const val PROJECT_SERVICES = "projectServices"

        /** Constant for the type of services that are defined via environment variables. */
        private const val ENVIRONMENT_SERVICES = "environmentServices"

        /**
         * Install this authenticator as the global default if it is not already installed. If the
         * [loadEnvironmentServices] flag is set, scan the current environment variables for definitions of
         * infrastructure services. In this case, use the provided [secretResolverFun] to resolve their credentials.
         */
        @Synchronized
        fun install(
            secretResolverFun: InfraSecretResolverFun = undefinedInfraSecretResolver,
            loadEnvironmentServices: Boolean = true
        ): OrtServerAuthenticator {
            val active = getDefault()
            return active as? OrtServerAuthenticator
                ?: OrtServerAuthenticator(active).also {
                    setDefault(it)
                    logger.info("OrtServerAuthenticator was successfully installed.")

                    if (loadEnvironmentServices) {
                       logger.info("Loading infrastructure services from environment variables.")
                       it.serviceDataByType[ENVIRONMENT_SERVICES] = loadEnvironmentServices(secretResolverFun)
                    }
                }
        }

        /**
         * Iterate over the currently defined environment variables to find variables that declare infrastructure
         * services. Use the given [secretResolverFun] to resolve their credentials.
         */
        private fun loadEnvironmentServices(secretResolverFun: InfraSecretResolverFun): ServiceData {
            val environmentServices = getAuthenticationInfoFromEnvironment(secretResolverFun)

            return ServiceData(
                    authenticationInfo = environmentServices,
                    authenticatedServices = AuthenticatedServices.create(
                        environmentServices.services,
                        enableFuzzyMatching = false
                    )
                )
        }
    }

    /**
     * A map storing a set of services for which authentication information is available for different types of
     * services. This supports different sources of authentication information which can be updated independently of
     * each other. It is not expected that the data is updated concurrently, but it may be accessed from different
     * threads. Therefore, a concurrent map is used to ensure safe publishing of changes.
     */
    private val serviceDataByType = ConcurrentHashMap<String, ServiceData>()

    /**
     * A reference to the listener to be notified about successful authentications. This listener can be set
     * dynamically when setting up the environment for a worker.
     */
    private val refListener = AtomicReference<AuthenticationListener>()

    /** The current authentication information for the different types of services. */
    val authenticationInfo
        get() = serviceDataByType.mapValues { it.value.authenticationInfo }

    override val delegateAuthenticators: List<Authenticator> = listOfNotNull(
        UserInfoAuthenticator(),
        ServicesAuthenticator(serviceDataByType, ENVIRONMENT_SERVICES, AtomicReference()),
        original,
        ServicesAuthenticator(serviceDataByType, PROJECT_SERVICES, refListener)
    )

    /**
     * Update the current [information about authentication][info] for the given [type] of services. This function is
     * called when there are changes in the credentials currently available, for instance, if new infrastructure
     * services are declared in the repository that is currently processed. It is also used to restore authentication
     * information that has been persisted earlier.
     */
    fun updateAuthenticationInfo(info: AuthenticationInfo, type: String = PROJECT_SERVICES) {
        logger.info("Updating the list of authenticated services. Setting ${info.services.size} services.")

        val authenticatedServices = AuthenticatedServices.create(
            info.services.filterNot { CredentialsType.NO_AUTHENTICATION in it.credentialsTypes },
            enableFuzzyMatching = true
        )

        serviceDataByType[type] = ServiceData(authenticatedServices, info)
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
    /** The object managing the known infrastructure services. */
    private val authenticatedServices: AuthenticatedServices,

    /** The object with authentication information. */
    val authenticationInfo: AuthenticationInfo
) {
    /**
     * Find the best-matching [ResolvedInfrastructureService] for the given [host] and optional [url].
     */
    fun getAuthenticatedService(host: String, url: URL?): ResolvedInfrastructureService? =
        authenticatedServices.getAuthenticatedServiceFor(host, url)
}

/** Constant for an empty [ServiceData] object to be used if an unknown type of services is queried. */
private val emptyServiceData = ServiceData(
    AuthenticatedServices.empty(),
    AuthenticationInfo(emptyMap(), emptyList())
)

/**
 * Implementation of an [Authenticator] which uses the current set of infrastructure services to authenticate requests.
 */
private class ServicesAuthenticator(
    /** A reference to the map of services for which authentication information is available. */
    private val servicesByType: ConcurrentMap<String, ServiceData>,

    /** The specific type of services this authenticator has to deal with. */
    private val serviceType: String,

    /** A reference to a listener to be notified on successful authentications. */
    private val authenticationListener: AtomicReference<AuthenticationListener>
) : Authenticator() {
    override fun getPasswordAuthentication(): PasswordAuthentication? {
        if (requestorType != RequestorType.SERVER) return null

        logger.info("Request for password authentication for '${requestingURL ?: requestingHost}'.")

        return with(servicesByType.getOrDefault(serviceType, emptyServiceData)) {
            getAuthenticatedService(requestingHost, requestingURL)?.let { service ->
                logger.info("Using credentials from service '${service.name}'.")

                authenticationListener.get()?.also { listener ->
                    listener.onAuthentication(AuthenticationEvent(service))
                }

                val username = authenticationInfo.resolveSecret(service.usernameSecret)
                val password = authenticationInfo.resolveSecret(service.passwordSecret)
                PasswordAuthentication(username, password.toCharArray())
            }
        }
    }
}
