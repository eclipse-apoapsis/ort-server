/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.transport.sqs

import io.kotest.core.extensions.install
import io.kotest.core.spec.Spec
import io.kotest.extensions.testcontainers.TestContainerSpecExtension

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.ConfigSecretProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.transport.testing.createConfigManager

import org.testcontainers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName

/**
 * Create a [ConfigManager] with a test queue for [consumerName] using [transportType] running on LocalStack.
 */
fun Spec.createSqsConfigManager(consumerName: String, transportType: String): ConfigManager {
    val localStack = install(
        TestContainerSpecExtension(
            LocalStackContainer(DockerImageName.parse("localstack/localstack:3.4.0")).withServices("sqs")
        )
    )

    val secretsMap = mapOf(
        ACCESS_KEY_ID_PROP_NAME to localStack.accessKey,
        SECRET_ACCESS_KEY_PROP_NAME to localStack.secretKey
    )

    val configProvidersMap = mapOf(
        ConfigManager.SECRET_PROVIDER_NAME_PROPERTY to ConfigSecretProviderFactoryForTesting.NAME,
        ConfigSecretProviderFactoryForTesting.SECRETS_PROPERTY to secretsMap
    )

    return createConfigManager(
        consumerName = consumerName,
        transportType = transportType,
        transportName = SqsConfig.TRANSPORT_NAME,
        serverUri = localStack.endpoint.toString(),
        configProvidersMap = configProvidersMap
    )
}
