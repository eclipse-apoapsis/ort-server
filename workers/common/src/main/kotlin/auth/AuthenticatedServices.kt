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

import org.eclipse.apoapsis.ortserver.workers.common.ResolvedInfrastructureService

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AuthenticatedServices::class.java)

/**
 * An internal helper class that manages a collection of resolved infrastructure services and offers functionality to
 * find a service that is the best match for a given URL.
 */
internal class AuthenticatedServices private constructor(
    /** A [Map] storing the known services grouped by their host name. */
    private val servicesByHost: Map<String, Collection<ResolvedInfrastructureService>>,

    /** A flag whether fuzzy search is enabled if no prefix match is found. */
    private val enableFuzzyMatching: Boolean
) {
    companion object {
        /**
         * Create an instance of [AuthenticatedServices] from a given collection of [services]. Use the
         * [enableFuzzyMatching] flag to control the behavior if no prefix match is found for a URL.
         */
        fun create(
            services: Collection<ResolvedInfrastructureService>,
            enableFuzzyMatching: Boolean
        ): AuthenticatedServices {
            val validatedServices = services.mapNotNull { service ->
                runCatching {
                    URI.create(service.url) to service
                }.onFailure {
                    logger.error(
                        "Invalid URI for service '${service.name}': '${service.url}'. Ignoring service.",
                        it
                    )
                }.getOrNull()
            }.groupBy { it.first.host }
                .mapValues { e ->
                    e.value.map { it.second.withTrailingSlash() }
                }
            return AuthenticatedServices(validatedServices, enableFuzzyMatching)
        }

        /**
         * Return an instance of [AuthenticatedServices] that does not contain any services and therefore cannot handle
         * any authentication requests.
         */
        fun empty(): AuthenticatedServices =
            AuthenticatedServices(emptyMap(), enableFuzzyMatching = false)
    }

    /**
     * Find the best-matching service to define the authentication for the given [host] and optional [url]. If there
     * are multiple services for the same host, try to find the best match. Return `null` if no unique matching service
     * is found.
     */
    fun getAuthenticatedServiceFor(host: String, url: URL?): ResolvedInfrastructureService? {
        val hostName = url?.host ?: host
        val services = servicesByHost[hostName].orEmpty()

        return (
                services.singleOrNull()?.takeIf { url == null }?.also {
                    logger.debug("Using single service for host '{}'.", hostName)
                } ?: findBestMatchingService(services, url, enableFuzzyMatching)
                )
    }
}

/**
 * Return a [ResolvedInfrastructureService] instance whose URI is guaranteed to end on a slash. This is needed for
 * correct prefix matching.
 */
private fun ResolvedInfrastructureService.withTrailingSlash(): ResolvedInfrastructureService =
    this.takeIf { url.endsWith('/') } ?: copy(url = "$url/")

/**
 * Try to find the best matching service in the given list of [services] for the given [url]. This function is used if
 * multiple services are available for the same host. It applies some heuristics to find the service whose URL is most
 * closely matching the given [url] if [enableFuzzyMatching] is *true*. If no services are available for the host,
 * result is *null*.
 */
private fun findBestMatchingService(
    services: Collection<ResolvedInfrastructureService>,
    url: URL?,
    enableFuzzyMatching: Boolean
): ResolvedInfrastructureService? {
    logger.debug(
        "Finding best matching service for '{}' from {}.",
        url?.toString(),
        services.joinToString { "${it.name} (${it.url})" }
    )

    val matchingServices = url?.let { requestUrl ->
        val strUrl = "${requestUrl.toString().removeSuffix("/")}/"
        services.filter { strUrl.startsWith(it.url) || it.url == strUrl }
    } ?: services

    return matchingServices.maxByOrNull { it.url.length }
        ?: findMostSimilarService(services, url, enableFuzzyMatching)
}

/**
 * An object for doing fuzzy matching of URLs to authenticate against service URLs. This is used as a heuristic if
 * there are multiple services defined for a host, but no prefix match is found.
 */
private val fuzzyScore = FuzzyScore(Locale.US)

/**
 * Try to find a service that most closely matches the given [url]. This function is called if no service for the URL
 * can be found based on prefix matching. If there are services at all and [enableFuzzyMatching] is *true*, it tries to
 * find the best match using a fuzzy search.
 */
private fun findMostSimilarService(
    services: Collection<ResolvedInfrastructureService>,
    url: URL?,
    enableFuzzyMatching: Boolean
): ResolvedInfrastructureService? {
    if (services.isEmpty() || url == null || !enableFuzzyMatching) return null

    if (services.size < 2) {
        logger.info(
            "Found only a single service for '{}', but there is no prefix match. " +
                    "Returning it, since fuzzy search is enabled.",
            url.host
        )
        return services.first()
    }

    val strUrl = url.toString().removeSuffix("/")
    logger.warn(
        "No unique infrastructure service found to match '{}'. Trying to find the best match. " +
                "If this yields an incorrect service, please declare one with a URL that is a prefix of this URL.",
        strUrl
    )

    val sortedServicesWithScores = services.map { service ->
        service to fuzzyScore.fuzzyScore(service.url.removeSuffix("/"), strUrl)
    }.sortedByDescending { it.second }

    return sortedServicesWithScores.first().takeUnless { sortedServicesWithScores[1].second == it.second }?.first
        .also { service ->
            if (service == null) {
                logger.warn(
                    "Found multiple services with the same matching score for '{}': {}.",
                    strUrl,
                    sortedServicesWithScores.takeWhile { it.second == sortedServicesWithScores.first().second }
                        .joinToString { "${it.first.name} (${it.first.url})" }
                )
            }
        }
}
