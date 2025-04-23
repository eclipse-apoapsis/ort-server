/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.EnvironmentServiceDefinition

import org.slf4j.LoggerFactory

/**
 * A specialized generator class to generate the content of the .netrc file.
 *
 * This generator class produces an entry for the _.netrc_ file for each [InfrastructureService] it is provided. See
 * https://daniel.haxx.se/blog/2022/05/31/netrc-pains/ for a discussion of the format and its limitations. The file
 * written by this class is intended to be read by ORT's _NetRcAuthenticator_ class. As this class does not support
 * any advanced features (e.g., special characters in passwords, quoting, or escaping), those aspects are ignored here
 * as well.
 *
 * For the _.netrc_ file, only the host names of machines are relevant. In case there are multiple infrastructure
 * services defined with URLs pointing to the same host, the credentials of the first one are used; so the order in
 * which services are passed is relevant. (Typically, infrastructure services are defined in configuration files
 * located in the repositories to be analyzed. Hence, it is possible for users to change the order in which the
 * services are listed as necessary.)
 */
class NetRcGenerator : EnvironmentConfigGenerator<EnvironmentServiceDefinition> {
    companion object {
        /** The name of the file to be generated. */
        private const val TARGET_NAME = ".netrc"

        private val logger = LoggerFactory.getLogger(NetRcGenerator::class.java)

        /**
         * Obtain the host name of this [InfrastructureService] from its URL or *null* if the URL is not valid.
         */
        private fun InfrastructureService.host(): String? =
            runCatching {
                URI.create(url).host
            }.onFailure {
                logger.error("Could not extract host for service '{}'. Ignoring it.", this, it)
            }.getOrNull()
    }

    override val environmentDefinitionType: Class<EnvironmentServiceDefinition> =
        EnvironmentServiceDefinition::class.java

    override suspend fun generate(builder: ConfigFileBuilder, definitions: Collection<EnvironmentServiceDefinition>) {
        val serviceHosts = definitions.filter { CredentialsType.NETRC_FILE in it.credentialsTypes() }
            .map { it.service to it.service.host() }
            .filter { it.second != null }
            .toMap()
        val deDuplicatedServices = serviceHosts.keys.groupBy { serviceHosts.getValue(it) }
            .values
            .map { it.first() }

        builder.buildInUserHome(TARGET_NAME) {
            deDuplicatedServices.forEach { service ->
                val host = URI.create(service.url).host
                val username = builder.secretRef(service.usernameSecret)
                val password = builder.secretRef(service.passwordSecret)

                println("machine $host login $username password $password")
            }
        }
    }
}
