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

package org.eclipse.apoapsis.ortserver.tasks

/**
 * An interface describing a task that can be executed.
 */
interface Task {
    /**
     * Execute this task.
     *
     * Tasks may fail with arbitrary exceptions. It is in the responsibility of the caller to catch these exceptions
     * and handle them. After this function returns, the task execution is considered complete, and the execution
     * environment may shut down; so, any kind of asynchronous background processing is not supported.
     */
    suspend fun execute()
}
