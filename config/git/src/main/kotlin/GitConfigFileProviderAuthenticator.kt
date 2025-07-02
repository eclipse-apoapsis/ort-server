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

package org.eclipse.apoapsis.ortserver.config.git

import java.net.Authenticator
import java.net.PasswordAuthentication

import org.ossreviewtoolkit.utils.ort.OrtAuthenticator

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(GitConfigFileProviderAuthenticator::class.java)

internal class GitConfigFileProviderAuthenticator(val username: String, val password: String) : OrtAuthenticator() {
    companion object {
        var original: Authenticator? = null

        @Synchronized
        fun install(username: String, password: String): GitConfigFileProviderAuthenticator {
            val active = getDefault()

            if (active is GitConfigFileProviderAuthenticator) return active

            original = active

            return GitConfigFileProviderAuthenticator(username, password).also {
                setDefault(it)
                logger.info("GitConfigFileProviderAuthenticator was successfully installed.")
            }
        }

        @Synchronized
        fun uninstall(): Authenticator? {
            val active = getDefault()

            return if (active is GitConfigFileProviderAuthenticator) {
                original.also {
                    setDefault(it)
                    logger.info("GitConfigFileProviderAuthenticator was successfully uninstalled.")
                }
            } else {
                logger.info("GitConfigFileProviderAuthenticator is not installed.")
                active
            }
        }
    }

    override fun getPasswordAuthentication() = PasswordAuthentication(username, password.toCharArray())
}

/**
 * Install an [GitConfigFileProviderAuthenticator] with the given [username] and [password] and execute the [block]. If
 * [username] or [password] is `null`, the [block] is executed without installing an authenticator.
 */
internal fun <T> withAuthenticator(username: String?, password: String?, block: () -> T): T {
    if (username == null || password == null) {
        return block()
    }

    try {
        GitConfigFileProviderAuthenticator.install(username, password)
        return block()
    } finally {
        GitConfigFileProviderAuthenticator.uninstall()
    }
}
