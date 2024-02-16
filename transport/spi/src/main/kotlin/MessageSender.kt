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

package org.eclipse.apoapsis.ortserver.transport

/**
 * An interface defining the mechanism to send a message to a specific endpoint.
 *
 * This interface allows sending messages of the correct type to an endpoint, abstracting over the concrete message
 * channel. It is used by the Orchestrator to send requests to be processed to the single workers, and from the
 * workers to send the corresponding results back to the Orchestrator.
 *
 * Since the exchange of messages is abstracted by an interface, the server can remain agnostic about the concrete
 * underlying infrastructure. Thus, it can be deployed in different environments.
 */
interface MessageSender<in T> {
    /**
     * Send [message] to the target endpoint of this [MessageSender].
     */
    fun send(message: Message<T>)
}
