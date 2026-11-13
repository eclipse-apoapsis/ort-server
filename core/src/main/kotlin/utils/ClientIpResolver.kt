/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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
package org.eclipse.apoapsis.ortserver.core.utils

import io.ktor.http.HttpHeaders
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.request.ApplicationRequest

import java.net.InetAddress

/**
 * The address ranges of proxies that are trusted by default: loopback, private, and link-local addresses. This covers
 * typical deployments where a reverse proxy or an ingress controller runs in the same private network as the core.
 */
internal const val DEFAULT_TRUSTED_PROXIES =
    "127.0.0.0/8,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,169.254.0.0/16,::1/128,fc00::/7,fe80::/10"

/**
 * A class to determine the IP address of the client that sent a request, taking into account the `X-Forwarded-For`
 * header set by proxies.
 *
 * As clients can set arbitrary values for this header, only entries added by [trustedProxies] are taken into
 * account: The chain of addresses consisting of the header entries followed by the remote address of the connection is
 * processed from right to left, skipping all addresses of trusted proxies. The first address that does not belong to a
 * trusted proxy is the client IP. If the remote address does not belong to a trusted proxy, the header is ignored. If
 * all addresses belong to trusted proxies, the leftmost one is used.
 */
class ClientIpResolver(private val trustedProxies: List<IpRange>) {
    companion object {
        /** The configuration property containing the comma-separated list of trusted proxy address ranges. */
        const val TRUSTED_PROXIES_PROPERTY = "ktor.trustedProxies"

        /**
         * Create a [ClientIpResolver] from the trusted proxies defined in the given [config], falling back to
         * [DEFAULT_TRUSTED_PROXIES] if the property is not set. An empty value means that no proxies are trusted.
         */
        fun create(config: ApplicationConfig): ClientIpResolver {
            val trustedProxies = config.propertyOrNull(TRUSTED_PROXIES_PROPERTY)?.getString()
                ?: DEFAULT_TRUSTED_PROXIES

            return ClientIpResolver(
                trustedProxies.split(',').map(String::trim).filter(String::isNotEmpty).map(IpRange::parse)
            )
        }
    }

    /** Return the IP address of the client that sent the given [request]. */
    fun resolve(request: ApplicationRequest): String =
        resolve(request.local.remoteAddress, request.headers.getAll(HttpHeaders.XForwardedFor).orEmpty())

    /**
     * Return the IP address of the client based on the [remoteAddress] of the connection and the values of the
     * [forwardedForHeaders].
     */
    fun resolve(remoteAddress: String, forwardedForHeaders: List<String>): String {
        val chain = forwardedForHeaders.flatMap { it.split(',') }.map(String::trim).filter(String::isNotEmpty) +
            remoteAddress

        return chain.asReversed().firstOrNull { !isTrustedProxy(it) } ?: chain.first()
    }

    private fun isTrustedProxy(address: String): Boolean =
        parseIpLiteral(address)?.let { ip -> trustedProxies.any { it.contains(ip) } } == true
}

/** A range of IP addresses defined by a [network] address and the length of the network [prefix] in bits. */
class IpRange(private val network: InetAddress, private val prefix: Int) {
    companion object {
        /** Parse the given [cidr] notation like "10.0.0.0/8". A single address is a range with only this address. */
        fun parse(cidr: String): IpRange {
            val address = cidr.substringBefore('/')
            val network = requireNotNull(parseIpLiteral(address)) { "Invalid IP address in range '$cidr'." }
            val maxPrefix = network.address.size * Byte.SIZE_BITS
            val prefix = if ('/' in cidr) cidr.substringAfter('/').toIntOrNull() else maxPrefix

            require(prefix != null && prefix in 0..maxPrefix) { "Invalid prefix length in range '$cidr'." }

            return IpRange(network, prefix)
        }
    }

    /** Return whether the given [address] is part of this range. */
    fun contains(address: InetAddress): Boolean {
        val networkBytes = network.address
        val addressBytes = address.address

        if (networkBytes.size != addressBytes.size) return false

        val fullBytes = prefix / Byte.SIZE_BITS
        if ((0 until fullBytes).any { networkBytes[it] != addressBytes[it] }) return false

        val remainingBits = prefix % Byte.SIZE_BITS
        if (remainingBits == 0) return true

        val mask = (0xFF shl (Byte.SIZE_BITS - remainingBits)) and 0xFF
        return (networkBytes[fullBytes].toInt() and mask) == (addressBytes[fullBytes].toInt() and mask)
    }
}

/**
 * Parse the given [address] as an IP address literal. Return *null* if it is not a valid literal. In contrast to
 * [InetAddress.getByName], this never performs a DNS lookup, which is important as the address may come from a header
 * controlled by the client.
 */
private fun parseIpLiteral(address: String): InetAddress? =
    runCatching { InetAddress.ofLiteral(address) }.getOrNull()
