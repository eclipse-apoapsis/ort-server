/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.services.ortrun

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import java.net.URI
import java.time.Instant

import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.Criticality
import org.ossreviewtoolkit.model.HealthMetric
import org.ossreviewtoolkit.model.HealthSummary

class HealthMetricMappingsTest : WordSpec({
    "The mappings of advisor results" should {
        "map the health metrics and the health summary in both directions" {
            val ortAdvisorResult = AdvisorResult(
                advisor = AdvisorDetails(name = "Scorecard"),
                summary = AdvisorSummary(
                    startTime = Instant.parse("2026-11-27T10:00:00Z"),
                    endTime = Instant.parse("2026-11-27T10:01:00Z")
                ),
                healthMetrics = listOf(
                    HealthMetric(
                        name = "Code-Review",
                        documentation = "Determines if the project requires human code review.",
                        documentationLink = URI.create("https://example.org/checks#code-review"),
                        minValue = 0.0,
                        maxValue = 10.0,
                        higherIsBetter = true,
                        value = 8.0,
                        criticality = Criticality.LOW,
                        reason = "Found 8/10 approved changesets.",
                        details = listOf("Info: first detail")
                    ),
                    HealthMetric(
                        name = "Packaging",
                        minValue = 0.0,
                        maxValue = 10.0,
                        higherIsBetter = true,
                        value = null,
                        reason = "packaging workflow not detected"
                    )
                ),
                healthSummary = HealthSummary(
                    overallScore = HealthMetric(
                        name = "Aggregate-Score",
                        minValue = 0.0,
                        maxValue = 10.0,
                        higherIsBetter = true,
                        value = 4.2,
                        criticality = Criticality.HIGH
                    ),
                    analyzedAt = Instant.parse("2026-11-23T00:00:00.123456Z"),
                    analyzedRevision = "479dbae",
                    toolVersion = "v5.7.0"
                )
            )

            ortAdvisorResult.mapToModel().mapToOrt() shouldBe ortAdvisorResult
        }
    }
})
