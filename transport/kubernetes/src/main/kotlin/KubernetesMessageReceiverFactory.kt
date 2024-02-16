/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import java.lang.Exception

import kotlin.system.exitProcess

import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.transport.Endpoint
import org.ossreviewtoolkit.server.transport.EndpointHandler
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessageHeader
import org.ossreviewtoolkit.server.transport.MessageReceiverFactory
import org.ossreviewtoolkit.server.transport.json.JsonSerializer

import org.slf4j.LoggerFactory

class KubernetesMessageReceiverFactory : MessageReceiverFactory {
    companion object {
        private val logger = LoggerFactory.getLogger(KubernetesMessageReceiverFactory::class.java)

        /**
         * Exit this process. This is necessary to make sure that the Java process terminates after the job has run.
         */
        internal fun exit(status: Int) {
            exitProcess(status)
        }
    }

    override val name = KubernetesSenderConfig.TRANSPORT_NAME

    override fun <T : Any> createReceiver(
        from: Endpoint<T>,
        configManager: ConfigManager,
        handler: EndpointHandler<T>
    ) {
        val serializer = JsonSerializer.forClass(from.messageClass)

        logger.info("Starting Kubernetes message receiver for endpoint '{}'.", from.configPrefix)

        val token = System.getenv("token")
        val traceId = System.getenv("traceId")
        val runId = System.getenv("runId").toLong()
        val payload = System.getenv("payload")

        val msg = Message(MessageHeader(token, traceId, runId), serializer.fromJson(payload))

        @Suppress("TooGenericExceptionCaught")
        try {
            handler(msg)
        } catch (e: Exception) {
            logger.error("Message processing caused an exception.", e)
            exit(1)
        } finally {
            exit(0)
        }
    }
}
