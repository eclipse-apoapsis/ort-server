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

package org.eclipse.apoapsis.ortserver.client

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.curl.CurlClientEngineConfig

import okio.FileSystem
import okio.Path.Companion.toPath

import org.eclipse.apoapsis.ortserver.utils.system.getEnv

/**
 * Environment variables that can be used to configure the SSL CA bundle path.
 */
private val caBundleEnvironmentVariables = sequenceOf("SSL_CERT_FILE", "CURL_CA_BUNDLE")

/**
 * Common paths where SSL CA certificates are typically located on Linux systems.
 */
private val commonCaBundlePaths = sequenceOf(
    "/etc/ssl/certs/ca-certificates.crt", // Debian/Ubuntu/Gentoo etc.
    "/etc/pki/tls/certs/ca-bundle.crt", // Fedora/RHEL/CentOS
    "/etc/ssl/ca-bundle.pem", // OpenSUSE
    "/etc/ssl/cert.pem", // Alpine Linux
    "/etc/pki/tls/cert.pem" // Amazon Linux
)

/**
 * Check if a file exists at the given [path].
 */
private fun fileExists(path: String): Boolean =
    FileSystem.SYSTEM.exists(path.toPath())

/**
 * Find the CA bundle path from environment variables or common system locations.
 */
private fun findCaBundlePath(): String? {
    // First, check environment variables
    caBundleEnvironmentVariables.firstNotNullOfOrNull { getEnv(it) }?.let { path ->
        if (fileExists(path)) return path
    }

    // Then check common system paths
    return commonCaBundlePaths.firstOrNull { fileExists(it) }
}

/**
 * Linux-specific implementation that configures the Curl engine to use the system CA bundle.
 * The Curl engine does not automatically use the system trust store, so we need to explicitly
 * configure the CA bundle path.
 */
@Suppress("UNCHECKED_CAST")
internal actual fun HttpClientConfig<*>.configurePlatformSpecificSsl() {
    val caBundlePath = findCaBundlePath()

    if (caBundlePath != null) {
        (this as? HttpClientConfig<CurlClientEngineConfig>)?.engine {
            sslVerify = true
            // Set the CA info path for the Curl engine to use the system CA bundle
            caInfo = caBundlePath
        }
    }
}
