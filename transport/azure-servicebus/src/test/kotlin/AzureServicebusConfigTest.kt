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

package org.eclipse.apoapsis.ortserver.transport.azureservicebus

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.config.ConfigManager

class AzureServicebusConfigTest : WordSpec({
    "createConfig" should {
        "create an instance from a ConfigManager object" {
            val namespace = "namespace"
            val queueName = "testQueue"

            val config = mockk<ConfigManager> {
                every { getString("namespace") } returns namespace
                every { getString("queueName") } returns queueName
            }

            val azureServicebusConfig = AzureServicebusConfig.createConfig(config)

            azureServicebusConfig.namespace shouldBe namespace
            azureServicebusConfig.queueName shouldBe queueName
        }
    }
})
