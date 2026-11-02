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

package org.eclipse.apoapsis.ortserver.orchestrator

import org.eclipse.apoapsis.ortserver.model.repositories.AdvisorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AnalyzerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.EvaluatorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.NotifierJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ReporterJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ScannerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.WorkerJobRepository

/**
 * A data class that holds references to the repositories of the worker jobs. This is used to have all these
 * repositories in one place, rather than passing around many references.
 */
class WorkerJobRepositories(
    /** The repository for Analyzer jobs. */
    val analyzerJobRepository: AnalyzerJobRepository,

    /** The repository for Advisor jobs. */
    val advisorJobRepository: AdvisorJobRepository,

    /** The repository for Scanner jobs. */
    val scannerJobRepository: ScannerJobRepository,

    /** The repository for Evaluator jobs. */
    val evaluatorJobRepository: EvaluatorJobRepository,

    /** The repository for Reporter jobs. */
    val reporterJobRepository: ReporterJobRepository,

    /** The repository for Notifier jobs. */
    val notifierJobRepository: NotifierJobRepository
) {
    /** Return the [WorkerJobRepository] for the jobs of the given [worker]. */
    internal operator fun get(worker: WorkerScheduleInfo): WorkerJobRepository<*> =
        when (worker) {
            WorkerScheduleInfo.ANALYZER -> analyzerJobRepository
            WorkerScheduleInfo.ADVISOR -> advisorJobRepository
            WorkerScheduleInfo.SCANNER -> scannerJobRepository
            WorkerScheduleInfo.EVALUATOR -> evaluatorJobRepository
            WorkerScheduleInfo.REPORTER -> reporterJobRepository
            WorkerScheduleInfo.NOTIFIER -> notifierJobRepository
        }
}
