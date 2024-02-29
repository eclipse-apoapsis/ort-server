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

package org.eclipse.apoapsis.ortserver.model.repositories

import org.eclipse.apoapsis.ortserver.model.NotifierJob
import org.eclipse.apoapsis.ortserver.model.NotifierJobConfiguration

interface NotifierJobRepository : WorkerJobRepository<NotifierJob> {
    /**
     * Create a notifier job.
     */
    fun create(ortRunId: Long, configuration: NotifierJobConfiguration): NotifierJob

    /**
     * Delete a notifier job by [id].
     */
    fun delete(id: Long)

    /**
     * Delete the recipients from the NotifierJob, as the email addresses are personal data.
     */
    fun deleteMailRecipients(id: Long): NotifierJob
}
