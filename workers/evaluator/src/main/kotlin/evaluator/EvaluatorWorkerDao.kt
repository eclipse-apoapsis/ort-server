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

package org.ossreviewtoolkit.server.workers.evaluator

import org.ossreviewtoolkit.server.model.EvaluatorJob
import org.ossreviewtoolkit.server.model.repositories.AdvisorJobRepository
import org.ossreviewtoolkit.server.model.repositories.AdvisorRunRepository
import org.ossreviewtoolkit.server.model.repositories.AnalyzerJobRepository
import org.ossreviewtoolkit.server.model.repositories.AnalyzerRunRepository
import org.ossreviewtoolkit.server.model.repositories.EvaluatorJobRepository
import org.ossreviewtoolkit.server.model.repositories.EvaluatorRunRepository
import org.ossreviewtoolkit.server.model.repositories.OrtRunRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository
import org.ossreviewtoolkit.server.model.repositories.ScannerJobRepository
import org.ossreviewtoolkit.server.model.repositories.ScannerRunRepository
import org.ossreviewtoolkit.server.model.runs.AnalyzerRun
import org.ossreviewtoolkit.server.model.runs.EvaluatorRun
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorRun
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerRun

class EvaluatorWorkerDao(
    private val advisorJobRepository: AdvisorJobRepository,
    private val advisorRunRepository: AdvisorRunRepository,
    private val analyzerJobRepository: AnalyzerJobRepository,
    private val analyzerRunRepository: AnalyzerRunRepository,
    private val evaluatorJobRepository: EvaluatorJobRepository,
    private val evaluatorRunRepository: EvaluatorRunRepository,
    private val ortRunRepository: OrtRunRepository,
    private val repositoryRepository: RepositoryRepository,
    private val scannerJobRepository: ScannerJobRepository,
    private val scannerRunRepository: ScannerRunRepository
) {
    fun getEvaluatorJob(jobId: Long) = evaluatorJobRepository.get(jobId)

    fun getOrtRun(ortRunId: Long) = ortRunRepository.get(ortRunId)

    fun getRepository(repositoryId: Long) = repositoryRepository.get(repositoryId)

    fun getAnalyzerRunForEvaluatorJob(evaluatorJob: EvaluatorJob): AnalyzerRun? {
        val ortRun = ortRunRepository.get(evaluatorJob.ortRunId) ?: return null
        val analyzerJob = analyzerJobRepository.getForOrtRun(ortRun.id) ?: return null
        return analyzerRunRepository.getByJobId(analyzerJob.id)
    }

    fun getAdvisorRunForEvaluatorJob(evaluatorJob: EvaluatorJob): AdvisorRun? {
        val ortRun = ortRunRepository.get(evaluatorJob.ortRunId) ?: return null
        val advisorJob = advisorJobRepository.getForOrtRun(ortRun.id) ?: return null
        return advisorRunRepository.getByJobId(advisorJob.id)
    }

    fun getScannerRunForEvaluatorJob(evaluatorJob: EvaluatorJob): ScannerRun? {
        val ortRun = ortRunRepository.get(evaluatorJob.ortRunId) ?: return null
        val scannerJob = scannerJobRepository.getForOrtRun(ortRun.id) ?: return null
        return scannerRunRepository.getByJobId(scannerJob.id)
    }

    fun storeEvaluatorRun(evaluatorRun: EvaluatorRun) {
        evaluatorRunRepository.create(
            evaluatorRun.evaluatorJobId,
            evaluatorRun.startTime,
            evaluatorRun.endTime,
            evaluatorRun.violations
        )
    }
}
