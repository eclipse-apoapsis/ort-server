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

package org.ossreviewtoolkit.server.workers.analyzer

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.server.model.AnalyzerJobConfiguration
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

class AnalyzerRunnerTest : WordSpec({
    val runner = AnalyzerRunner()

    "run" should {
        "return the correct repository information" {
            val inputDir = createOrtTempDir().resolve("project")
            inputDir.safeMkdirs()

            val result = runner.run(inputDir, AnalyzerJobConfiguration()).repository

            // TODO: Improve test so that the objects below are not all empty.
            result.config shouldBe RepositoryConfiguration()
            result.vcs shouldBe VcsInfo.EMPTY
            result.vcsProcessed shouldBe VcsInfo.EMPTY
            result.nestedRepositories should beEmpty()
        }

        "return an unmanaged project for a directory with only an empty subdirectory" {
            val inputDir = createOrtTempDir().resolve("project")
            inputDir.resolve("subdirectory").safeMkdirs()

            val result = runner.run(inputDir, AnalyzerJobConfiguration()).analyzer?.result

            result.shouldNotBeNull()
            result.projects.map { it.id } should containExactly(Identifier("Unmanaged::project"))
        }
    }
})
