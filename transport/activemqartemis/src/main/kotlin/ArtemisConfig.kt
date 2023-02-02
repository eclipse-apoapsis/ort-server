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

/**
 * A class defining the configuration settings used by the ActiveMQ Artemis Transport implementation.
 */
data class ArtemisConfig(
    /** The URI where the server broker is running. */
    val serverUri: String,

    /** The name of the queue that is used for sending and receiving messages. */
    val queueName: String
) {
    companion object {
        /**
         * Constant for the name of this transport implementation. This name is used for both the message sender and
         * receiver factories.
         */
        const val TRANSPORT_NAME = "activeMQ"

        /** Name of the configuration property for the server URI. */
        private const val SERVER_URI_PROPERTY = "serverUri"

        /** Name of the configuration property for the queue name. */
        private const val QUEUE_NAME_PROPERTY = "queueName"

        /**
         * Create an [ArtemisConfig] object from the provided [config].
         */
        fun createConfig(config: Config) =
            ArtemisConfig(
                serverUri = config.getString(SERVER_URI_PROPERTY),
                queueName = config.getString(QUEUE_NAME_PROPERTY)
            )
    }
}
