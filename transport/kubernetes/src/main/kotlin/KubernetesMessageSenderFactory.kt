/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.transport.kubernetes

import com.typesafe.config.Config

import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.util.ClientBuilder.defaultClient

import org.ossreviewtoolkit.server.transport.Endpoint
import org.ossreviewtoolkit.server.transport.MessageSender
import org.ossreviewtoolkit.server.transport.MessageSenderFactory

class KubernetesMessageSenderFactory : MessageSenderFactory {
    override val name: String = KubernetesConfig.TRANSPORT_NAME

    override fun <T : Any> createSender(to: Endpoint<T>, config: Config): MessageSender<T> {
        val senderConfig = KubernetesConfig.createConfig(config)

        val client = defaultClient().setDebugging(senderConfig.enableDebugLogging)

        return KubernetesMessageSender(
            api = BatchV1Api(client),
            config = senderConfig,
            endpoint = to
        )
    }
}
