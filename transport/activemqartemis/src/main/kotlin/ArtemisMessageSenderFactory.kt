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

package org.ossreviewtoolkit.server.transport.artemis

import com.typesafe.config.Config

import org.apache.qpid.jms.JmsConnectionFactory

import org.ossreviewtoolkit.server.transport.Endpoint
import org.ossreviewtoolkit.server.transport.MessageSender
import org.ossreviewtoolkit.server.transport.MessageSenderFactory

/**
 * Implementation of the [MessageSenderFactory] interface for Apache ActiveMQ Artemis.
 */
class ArtemisMessageSenderFactory : MessageSenderFactory {
    override val name: String = "activeMQ"

    override fun <T : Any> createSender(to: Endpoint<T>, config: Config): MessageSender<T> {
        val artemisConfig = ArtemisConfig.create(to, config)
        return createSenderWithConnection(to, JmsConnectionFactory(artemisConfig.serverUri), artemisConfig.queueName)
    }

    /**
     * Create a [MessageSender] for Artemis that sends message to the given [endpoint][to] via the queue with the
     * given [queueName]. Use [connectionFactory] to set up the JMS object and handle exceptions gracefully.
     */
    internal fun <T : Any> createSenderWithConnection(
        to: Endpoint<T>,
        connectionFactory: JmsConnectionFactory,
        queueName: String
    ): MessageSender<T> {
        val connection = connectionFactory.createConnection()

        return runCatching {
            val session = connection.createSession()
            val queue = session.createQueue(queueName)
            val producer = session.createProducer(queue)

            ArtemisMessageSender(connection, session, producer, to)
        }.onFailure {
            connection.close()
        }.getOrThrow()
    }
}
