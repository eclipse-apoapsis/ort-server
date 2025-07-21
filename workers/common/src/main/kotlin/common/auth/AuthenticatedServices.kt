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

import java.net.URI
import java.net.URL
import java.util.Locale

import org.apache.commons.text.similarity.FuzzyScore

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AuthenticatedServices::class.java)

/**
 * An internal helper class that manages a collection of services of type [T] and offers functionality to find a
 * service that is the best match for a given URL.
 */
internal class AuthenticatedServices<T> private constructor(
    /** A [Map] storing the known services grouped by their host name. */
    private val servicesByHost: Map<String, Collection<AuthenticatedService<T>>>
) {
    companion object {
        /**
         * Create an instance of [AuthenticatedServices] from a given collection of [services]. Use the given
         * [urlExtractor] and [nameExtractor] functions to obtain the corresponding properties from the services.
         */
        fun <T> create(
            services: Collection<T>,
            urlExtractor: (T) -> String,
            nameExtractor: (T) -> String
        ): AuthenticatedServices<T> {
            val validatedServices = services.mapNotNull { service ->
                runCatching {
                    val authService = AuthenticatedService(service, urlExtractor(service), nameExtractor(service))
                    URI.create(authService.serviceUrl) to authService
                }.onFailure {
                    logger.error(
                        "Invalid URI for service '${nameExtractor(service)}': '${urlExtractor(service)}'. " +
                                "Ignoring service.",
                        it
                    )
                }.getOrNull()
            }.groupBy { it.first.host }
                .mapValues { e ->
                    e.value.map { it.second.withTrailingSlash() }
                }
            return AuthenticatedServices(validatedServices)
        }

        /**
         * Return an instance of [AuthenticatedServices] that does not contain any services and therefore cannot handle
         * any authentication requests.
         */
        fun <T> empty(): AuthenticatedServices<T> =
            AuthenticatedServices(emptyMap())
    }

    /**
     * Find the best-matching service to define the authentication for the given [host] and optional [url]. If there
     * are multiple services for the same host, try to find the best match. Return `null` if no unique matching service
     * is found.
     */
    fun getAuthenticatedServiceFor(host: String, url: URL?): T? {
        val hostName = url?.host ?: host
        val services = servicesByHost[hostName].orEmpty()

        return (
                services.singleOrNull()?.also {
                    logger.debug("Using single service for host '{}'.", hostName)
                } ?: findBestMatchingService(services, url)
                )?.service
    }
}

/**
 * An internally used data class that holds the relevant information about a single service that requires
 * authentication.
 */
private data class AuthenticatedService<T>(
    /** The reference to the service. */
    val service: T,

    /** The URL of this service. */
    val serviceUrl: String,

    /** A name for this service. */
    val serviceName: String
)

/**
 * Return an [AuthenticatedService] instance whose URI is guaranteed to end on a slash. This is needed for correct
 * prefix matching.
 */
private fun <T> AuthenticatedService<T>.withTrailingSlash(): AuthenticatedService<T> =
    this.takeIf { serviceUrl.endsWith('/') } ?: copy(serviceUrl = "$serviceUrl/")

/**
 * Try to find the best matching service in the given list of [services] for the given [url]. This function is used if
 * multiple services are available for the same host. It applies some heuristics to find the service whose URL is most
 * closely matching the given [url]. If no services are available for the host, result is *null*.
 */
private fun <T> findBestMatchingService(
    services: Collection<AuthenticatedService<T>>,
    url: URL?
): AuthenticatedService<T>? {
    logger.debug(
        "Finding best matching service for '{}' from {}.",
        url?.toString(),
        services.joinToString { "${it.serviceName} (${it.serviceUrl})" }
    )

    val matchingServices = url?.let { requestUrl ->
        val strUrl = "${requestUrl.toString().removeSuffix("/")}/"
        services.filter { strUrl.startsWith(it.serviceUrl) || it.serviceUrl == strUrl }
    } ?: services

    return matchingServices.maxByOrNull { it.serviceUrl.length } ?: findMostSimilarService(services, url)
}

/**
 * An object for doing fuzzy matching of URLs to authenticate against service URLs. This is used as a heuristic if
 * there are multiple services defined for a host, but no prefix match is found.
 */
private val fuzzyScore = FuzzyScore(Locale.US)

/**
 * Try to find a service that most closely matches the given [url]. This function is called if no service for the URL
 * can be found based on prefix matching. If there are services at all, it tries to find the best match using a fuzzy
 * search.
 */
private fun <T> findMostSimilarService(
    services: Collection<AuthenticatedService<T>>,
    url: URL?
): AuthenticatedService<T>? =
    if (services.isEmpty() || url == null) {
        null
    } else {
        val strUrl = url.toString().removeSuffix("/")
        logger.warn(
            "No unique infrastructure service found to match '{}'. Trying to find the best match. " +
                    "If this yields an incorrect service, please declare one with a URL that is a prefix of this URL.",
            strUrl
        )

        val sortedServicesWithScores = services.map { service ->
            service to fuzzyScore.fuzzyScore(service.serviceUrl.removeSuffix("/"), strUrl)
        }.sortedByDescending { it.second }

        sortedServicesWithScores.first().takeUnless { sortedServicesWithScores[1].second == it.second }?.first
            .also { service ->
                if (service == null) {
                    logger.warn(
                        "Found multiple services with the same matching score for '{}': {}.",
                        strUrl,
                        sortedServicesWithScores.takeWhile { it.second == sortedServicesWithScores.first().second }
                            .joinToString { "${it.first.serviceName} (${it.first.serviceUrl})" }
                    )
                }
            }
    }
