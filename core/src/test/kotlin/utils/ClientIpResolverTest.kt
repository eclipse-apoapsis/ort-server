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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

import io.ktor.server.config.MapApplicationConfig

import java.net.InetAddress

class ClientIpResolverTest : WordSpec({
    val resolver = ClientIpResolver.create(MapApplicationConfig())

    "resolve()" should {
        "return the remote address if there is no X-Forwarded-For header" {
            resolver.resolve("10.0.0.1", emptyList()) shouldBe "10.0.0.1"
        }

        "ignore the header if the remote address is not a trusted proxy" {
            resolver.resolve("203.0.113.1", listOf("198.51.100.7")) shouldBe "203.0.113.1"
        }

        "return the address added by a trusted proxy" {
            resolver.resolve("10.0.0.1", listOf("198.51.100.7")) shouldBe "198.51.100.7"
        }

        "ignore addresses set by the client" {
            resolver.resolve("10.0.0.1", listOf("192.0.2.99, 198.51.100.7")) shouldBe "198.51.100.7"
        }

        "skip multiple trusted proxies" {
            resolver.resolve("10.0.0.1", listOf("198.51.100.7, 192.168.1.5, 172.16.0.3")) shouldBe "198.51.100.7"
        }

        "process multiple header lines in order" {
            resolver.resolve("10.0.0.1", listOf("192.0.2.99", "198.51.100.7, 10.0.0.2")) shouldBe "198.51.100.7"
        }

        "return the leftmost address if all addresses are trusted proxies" {
            resolver.resolve("10.0.0.1", listOf("192.168.1.5, 10.0.0.2")) shouldBe "192.168.1.5"
        }

        "treat entries that are not IP addresses as the client" {
            resolver.resolve("10.0.0.1", listOf("198.51.100.7, unknown")) shouldBe "unknown"
            resolver.resolve("10.0.0.1", listOf("198.51.100.7, localhost")) shouldBe "localhost"
        }

        "support IPv6 addresses" {
            resolver.resolve("::1", listOf("2001:db8::1, fd00::2")) shouldBe "2001:db8::1"
        }

        "ignore the header if no proxies are trusted" {
            val noProxiesResolver = ClientIpResolver.create(
                MapApplicationConfig(ClientIpResolver.TRUSTED_PROXIES_PROPERTY to "")
            )

            noProxiesResolver.resolve("10.0.0.1", listOf("198.51.100.7")) shouldBe "10.0.0.1"
        }

        "only trust the configured proxies" {
            val customResolver = ClientIpResolver.create(
                MapApplicationConfig(ClientIpResolver.TRUSTED_PROXIES_PROPERTY to "203.0.113.0/24, 10.1.2.3")
            )

            customResolver.resolve("10.1.2.3", listOf("198.51.100.7, 203.0.113.9")) shouldBe "198.51.100.7"
            customResolver.resolve("10.0.0.1", listOf("198.51.100.7")) shouldBe "10.0.0.1"
        }
    }

    "IpRange" should {
        "contain the addresses within the prefix" {
            val range = IpRange.parse("172.16.0.0/12")

            range.contains(ip("172.16.0.0")).shouldBeTrue()
            range.contains(ip("172.31.255.255")).shouldBeTrue()
            range.contains(ip("172.15.255.255")).shouldBeFalse()
            range.contains(ip("172.32.0.0")).shouldBeFalse()
        }

        "contain only the address itself if there is no prefix" {
            val range = IpRange.parse("10.1.2.3")

            range.contains(ip("10.1.2.3")).shouldBeTrue()
            range.contains(ip("10.1.2.4")).shouldBeFalse()
        }

        "support IPv6 ranges" {
            val range = IpRange.parse("fc00::/7")

            range.contains(ip("fd12:3456::1")).shouldBeTrue()
            range.contains(ip("fe80::1")).shouldBeFalse()
        }

        "not contain addresses of a different IP version" {
            IpRange.parse("0.0.0.0/0").contains(ip("::1")).shouldBeFalse()
        }

        "contain IPv4-mapped IPv6 addresses in IPv4 ranges" {
            IpRange.parse("10.0.0.0/8").contains(ip("::ffff:10.0.0.1")).shouldBeTrue()
        }

        "reject invalid ranges" {
            shouldThrow<IllegalArgumentException> { IpRange.parse("example.com/8") }
            shouldThrow<IllegalArgumentException> { IpRange.parse("10.0.0.0/33") }
            shouldThrow<IllegalArgumentException> { IpRange.parse("10.0.0.0/x") }
        }
    }
})

private fun ip(address: String): InetAddress = InetAddress.ofLiteral(address)
