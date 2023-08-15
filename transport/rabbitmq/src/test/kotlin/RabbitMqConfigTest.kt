/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.transport.rabbitmq

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk

import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.config.Path

class RabbitMqConfigTest : WordSpec({
    "createConfig" should {
        "create an instance from a ConfigManager object" {
            val serverUri = "tcp://example.org:5445"
            val queueName = "testQueue"
            val username = "user"
            val password = "pass"

            val config = mockk<ConfigManager> {
                every { getString("serverUri") } returns serverUri
                every { getString("queueName") } returns queueName
                every { getSecret(Path("rabbitMqUser")) } returns username
                every { getSecret(Path("rabbitMqPassword")) } returns password
            }

            val rabbitMqConfig = RabbitMqConfig.createConfig(config)

            rabbitMqConfig.serverUri shouldBe serverUri
            rabbitMqConfig.queueName shouldBe queueName
            rabbitMqConfig.username shouldBe username
            rabbitMqConfig.password shouldBe password
        }
    }
})
