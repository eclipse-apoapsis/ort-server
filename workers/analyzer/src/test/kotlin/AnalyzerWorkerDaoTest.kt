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

package org.ossreviewtoolkit.server.workers.analyzer

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import org.jetbrains.exposed.sql.transactions.transaction

import org.ossreviewtoolkit.server.dao.repositories.DaoAnalyzerJobRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoAnalyzerRunRepository
import org.ossreviewtoolkit.server.dao.tables.AnalyzerJobDao
import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.dao.test.Fixtures
import org.ossreviewtoolkit.server.workers.common.mapToModel

class AnalyzerWorkerDaoTest : WordSpec({
    lateinit var dao: AnalyzerWorkerDao
    lateinit var fixtures: Fixtures

    extension(
        DatabaseTestExtension { db ->
            dao = AnalyzerWorkerDao(DaoAnalyzerJobRepository(db), DaoAnalyzerRunRepository(db))
            fixtures = Fixtures(db)
        }
    )

    "getAnalyzerJob" should {
        "return job if job does exist" {
            val job = dao.getAnalyzerJob(fixtures.analyzerJob.id)

            job shouldBe fixtures.analyzerJob
        }

        "return null if job does not exist" {
            dao.getAnalyzerJob(-1L) shouldBe null
        }
    }

    "storeAnalyzerRun" should {
        "store the run correctly" {
            val projectDir = File("src/test/resources/mavenProject").absoluteFile
            val run = AnalyzerRunner().run(
                projectDir,
                fixtures.analyzerJob.configuration
            ).analyzer!!.mapToModel(fixtures.analyzerJob.id)

            dao.storeAnalyzerRun(run)

            val storedRun = transaction {
                AnalyzerJobDao[fixtures.analyzerJob.id].analyzerRun?.mapToModel()
            }

            storedRun.shouldNotBeNull()
            with(storedRun) {
                analyzerJobId shouldBe fixtures.analyzerJob.id
                environment shouldBe run.environment
                config shouldBe run.config.copy(packageManagers = emptyMap())
                projects should containExactly(run.projects)
                packages should containExactly(run.packages)
                issues should beEmpty()
                dependencyGraphs shouldBe run.dependencyGraphs
            }
        }
    }
})
