/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.core.apiDocs

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute

import io.ktor.http.HttpStatusCode

import kotlin.time.Duration.Companion.minutes

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.api.v1.model.ComparisonOperator
import org.eclipse.apoapsis.ortserver.api.v1.model.EcosystemStats
import org.eclipse.apoapsis.ortserver.api.v1.model.FilterOperatorAndValue
import org.eclipse.apoapsis.ortserver.api.v1.model.Identifier
import org.eclipse.apoapsis.ortserver.api.v1.model.Issue
import org.eclipse.apoapsis.ortserver.api.v1.model.JobSummaries
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunFilters
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatistics
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunSummary
import org.eclipse.apoapsis.ortserver.api.v1.model.Package
import org.eclipse.apoapsis.ortserver.api.v1.model.PagedResponse
import org.eclipse.apoapsis.ortserver.api.v1.model.PagedSearchResponse
import org.eclipse.apoapsis.ortserver.api.v1.model.PagingData
import org.eclipse.apoapsis.ortserver.api.v1.model.ProcessedDeclaredLicense
import org.eclipse.apoapsis.ortserver.api.v1.model.RemoteArtifact
import org.eclipse.apoapsis.ortserver.api.v1.model.RepositoryType
import org.eclipse.apoapsis.ortserver.api.v1.model.RuleViolation
import org.eclipse.apoapsis.ortserver.api.v1.model.Severity
import org.eclipse.apoapsis.ortserver.api.v1.model.ShortestDependencyPath
import org.eclipse.apoapsis.ortserver.api.v1.model.SortDirection
import org.eclipse.apoapsis.ortserver.api.v1.model.SortProperty
import org.eclipse.apoapsis.ortserver.api.v1.model.VcsInfo
import org.eclipse.apoapsis.ortserver.api.v1.model.Vulnerability
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityRating
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityReference
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityWithIdentifier
import org.eclipse.apoapsis.ortserver.model.LogLevel
import org.eclipse.apoapsis.ortserver.model.LogSource

val getOrtRunById: OpenApiRoute.() -> Unit = {
    operationId = "getOrtRunById"
    summary = "Get details of an ORT run."
    tags = listOf("Runs")

    request {
        pathParameter<Long>("runId") {
            description = "The run's ID."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<OrtRun> {
                example("Get ORT run") {
                    value = OrtRun(
                        id = 1,
                        index = 2,
                        organizationId = 1,
                        productId = 1,
                        repositoryId = 1,
                        revision = "main",
                        createdAt = CREATED_AT,
                        jobConfigs = fullJobConfigurations,
                        resolvedJobConfigs = fullJobConfigurations,
                        jobs = jobs,
                        status = OrtRunStatus.ACTIVE,
                        finishedAt = null,
                        labels = mapOf("label key" to "label value"),
                        issues = emptyList(),
                        jobConfigContext = null,
                        resolvedJobConfigContext = "32f955941e94d0a318e1c985903f42af924e9050",
                        traceId = "35b67724-a85a-4cc4-b2a4-60fd914634e7",
                        environmentConfigPath = null
                    )
                }
            }
        }
    }
}

val deleteOrtRunById: OpenApiRoute.() -> Unit = {
    operationId = "deleteOrtRunById"
    summary = "Delete an ORT run."
    description = "This operation deletes an ORT run and all generated data, including the generated reports."
    tags = listOf("Runs")

    request {
        pathParameter<Long>("runId") {
            description = "The run's ID."
        }
    }

    response {
        HttpStatusCode.NoContent to {
            description = "Successfully deleted the ORT run."
        }

        HttpStatusCode.NotFound to {
            description = "The ORT run does not exist."
        }
    }
}

val getReportByRunIdAndFileName: OpenApiRoute.() -> Unit = {
    operationId = "GetReportByRunIdAndFileName"
    summary = "Download a report of an ORT run."
    tags = listOf("Reports")

    request {
        pathParameter<Long>("runId") {
            description = "The ID of the ORT run."
        }
        pathParameter<String>("fileName") {
            description = "The name of the report file to be downloaded."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success. The response body contains the requested report file."
            header<String>("Content-Type") {
                description = "The content type is set to the media type derived from the report file."
            }
        }

        HttpStatusCode.NotFound to {
            description = "The requested report file or the ORT run could not be resolved."
        }
    }
}

val getLogsByRunId: OpenApiRoute.() -> Unit = {
    operationId = "GetLogsByRunId"
    summary = "Download an archive with selected logs of an ORT run."
    tags = listOf("Logs")

    request {
        pathParameter<Long>("runId") {
            description = "The ID of the ORT run."
        }

        queryParameter<String>("level") {
            description = "The log level; can be one of " +
                    LogLevel.entries.joinToString { "'$it'" } + " (ignoring case)." +
                    "Only logs of this level or higher are retrieved. Defaults to 'INFO' if missing."
        }

        queryParameter<String>("steps") {
            description = "Defines the run steps for which logs are to be retrieved. This is a comma-separated " +
                    "string with the following allowed steps: " + LogSource.entries.joinToString { "'$it'" } +
                    " (ignoring case). If missing, the logs for all steps are retrieved."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success. The response body contains a Zip archive with the selected log files."
        }

        HttpStatusCode.NotFound to {
            description = "The ORT run does not exist."
        }

        HttpStatusCode.BadRequest to {
            description = "Invalid values have been provided for the log level or steps parameters."
        }
    }
}

val getIssuesByRunId: OpenApiRoute.() -> Unit = {
    operationId = "GetIssuesByRunId"
    summary = "Get the issues of an ORT run."
    tags = listOf("Issues")

    request {
        pathParameter<Long>("runId") {
            description = "The ID of the ORT run."
        }

        standardListQueryParameters()
    }

    response {
        HttpStatusCode.OK to {
            description = "Success."
            jsonBody<PagedResponse<Issue>> {
                example("Get issues for an ORT run") {
                    value = PagedResponse(
                        listOf(
                            Issue(
                                message = "An issue",
                                severity = Severity.ERROR,
                                source = "source",
                                timestamp = CREATED_AT
                            )
                        ),
                        PagingData(
                            limit = 20,
                            offset = 0,
                            totalCount = 1,
                            sortProperties = listOf(SortProperty("timestamp", SortDirection.DESCENDING))
                        )
                    )
                }
            }
        }

        HttpStatusCode.NotFound to {
            description = "The ORT run does not exist."
        }
    }
}

val getVulnerabilitiesByRunId: OpenApiRoute.() -> Unit = {
    operationId = "GetVulnerabilitiesByRunId"
    summary = "Get the vulnerabilities found in an ORT run."
    tags = listOf("Vulnerabilities")

    request {
        pathParameter<Long>("runId") {
            description = "The ID of the ORT run."
        }

        standardListQueryParameters()
    }

    response {
        HttpStatusCode.OK to {
            description = "Success."
            jsonBody<PagedResponse<VulnerabilityWithIdentifier>> {
                example("Get vulnerabilities for an ORT run") {
                    value = PagedResponse(
                        listOf(
                            VulnerabilityWithIdentifier(
                                vulnerability = Vulnerability(
                                    externalId = "CVE-2021-1234",
                                    summary = "A vulnerability",
                                    description = "A description",
                                    references = listOf(
                                        VulnerabilityReference(
                                            "https://example.com",
                                            "CVSS3",
                                            "HIGH",
                                            9.8f,
                                            "CVSS:3.0/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"
                                        )
                                    )
                                ),
                                identifier = Identifier("Maven", "org.namespace", "name", "1.0"),
                                rating = VulnerabilityRating.HIGH
                            )
                        ),
                        PagingData(
                            limit = 20,
                            offset = 0,
                            totalCount = 1,
                            sortProperties = listOf(SortProperty("external_id", SortDirection.ASCENDING))
                        )
                    )
                }
            }
        }

        HttpStatusCode.NotFound to {
            description = "The ORT run does not exist."
        }
    }
}

val getRuleViolationsByRunId: OpenApiRoute.() -> Unit = {
    operationId = "GetRuleViolationsByRunId"
    summary = "Get the rule violations found in an ORT run."
    tags = listOf("RuleViolations")

    request {
        pathParameter<Long>("runId") {
            description = "The ID of the ORT run."
        }

        standardListQueryParameters()
    }

    response {
        HttpStatusCode.OK to {
            description = "Success."
            jsonBody<PagedResponse<RuleViolation>> {
                example("Get rule violations for an ORT run") {
                    value = PagedResponse(
                        listOf(
                            RuleViolation(
                                "Unmapped declared license found",
                                "GPL-1.0-or-later",
                                "DETECTED",
                                Severity.ERROR,
                                "The declared license 'LPGL-2.1' could not be mapped to a valid SPDX expression.",
                                """
                                    |Please add a declared license mapping via a curation for package
                                    |'SpdxDocumentFile::hal:7.70.0'.
                                    |If this is a false-positive or ineffective finding, it can be fixed in your 
                                    |`.ort.yml` file:
                                    |```yaml
                                    |---
                                    |curations:
                                    |  packages:
                                    |  - id: \"SpdxDocumentFile::hal:7.70.0\"
                                    |    curations:
                                    |      comment: \"<Describe the reason for the curation.>\"
                                    |      declared_license_mapping:
                                    |        LPGL-2.1: <Insert correct license.>
                                    |```
                                    |Documentation in how to configure curations in the `.ort.yml` file can be found
                                    |[here](https://oss-review-toolkit.org/ort/docs/configuration/ort-yml).
                                    """.trimMargin(),
                                Identifier(
                                    "Maven",
                                    "org.glassfish.jersey.media",
                                    "jersey-media-jaxb",
                                    "2.42"
                                )

                            )
                        ),
                        PagingData(
                            limit = 20,
                            offset = 0,
                            totalCount = 1,
                            sortProperties = listOf(SortProperty("rule", SortDirection.ASCENDING))
                        )
                    )
                }
            }
        }

        HttpStatusCode.NotFound to {
            description = "The ORT run does not exist."
        }
    }
}

val getPackagesByRunId: OpenApiRoute.() -> Unit = {
    operationId = "GetPackagesByRunId"
    summary = "Get the packages found in an ORT run."
    tags = listOf("Packages")

    request {
        pathParameter<Long>("runId") {
            description = "The ID of the ORT run."
        }

        standardListQueryParameters()
    }

    response {
        HttpStatusCode.OK to {
            description = "Success."
            jsonBody<PagedResponse<Package>> {
                example("Get packages for an ORT run") {
                    value = PagedResponse(
                        listOf(
                            Package(
                                identifier = Identifier("Maven", "org.example", "name", "1.0"),
                                purl = "pkg:maven/org.example/name@1.0",
                                cpe = null,
                                authors = setOf("author1", "author2"),
                                declaredLicenses = setOf("license1", "license2"),
                                processedDeclaredLicense = ProcessedDeclaredLicense(
                                    spdxExpression = "Expression",
                                    mappedLicenses = emptyMap(),
                                    unmappedLicenses = emptySet()
                                ),
                                description = "A description",
                                homepageUrl = "https://example.com/namespace/name",
                                binaryArtifact = RemoteArtifact("url", "hashValue", "hashAlgorithm"),
                                sourceArtifact = RemoteArtifact("url", "hashValue", "hashAlgorithm"),
                                vcs = VcsInfo(RepositoryType.GIT.name, "url", "revision", "path"),
                                vcsProcessed = VcsInfo(RepositoryType.GIT.name, "url", "revision", "path"),
                                isMetadataOnly = false,
                                isModified = false,
                                shortestDependencyPaths = listOf(
                                    ShortestDependencyPath(
                                        scope = "productionRuntimeClasspath",
                                        projectIdentifier = Identifier("Gradle", "", "project-name", "1.0"),
                                        path = listOf(
                                            Identifier("Maven", "org.example", "some", "1.0"),
                                            Identifier("Maven", "org.example", "other", "1.0")
                                        )
                                    )
                                )
                            )
                        ),
                        PagingData(
                            limit = 20,
                            offset = 0,
                            totalCount = 1,
                            sortProperties = listOf(SortProperty("purl", SortDirection.ASCENDING))
                        )
                    )
                }
            }
        }

        HttpStatusCode.NotFound to {
            description = "The ORT run does not exist."
        }
    }
}

val getOrtRuns: OpenApiRoute.() -> Unit = {
    operationId = "getOrtRuns"
    summary = "Get all ORT runs."
    tags = listOf("Runs")

    request {
        queryParameter<String>("status") {
            description = "Defines the statuses for which runs are to be retrieved. This is a comma-separated " +
                    "string with the following allowed statuses: " + OrtRunStatus.entries.joinToString { "'$it'" } +
                    " (ignoring case). If missing, the runs for all statuses are retrieved. Add a minus as the first " +
                    "item to exclude runs with the specified status(es), e.g. '-,FINISHED'."
        }

        standardListQueryParameters()
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<PagedSearchResponse<OrtRunSummary, OrtRunFilters>> {
                example("Get ORT runs for server instance") {
                    value = PagedSearchResponse(
                        listOf(
                            OrtRunSummary(
                                id = 1,
                                index = 2,
                                organizationId = 1,
                                productId = 1,
                                repositoryId = 1,
                                revision = "main",
                                createdAt = Clock.System.now(),
                                finishedAt = Clock.System.now(),
                                jobs = JobSummaries(
                                    analyzer = createJobSummary(10.minutes),
                                    advisor = createJobSummary(8.minutes),
                                    scanner = createJobSummary(8.minutes),
                                    evaluator = createJobSummary(6.minutes),
                                    reporter = createJobSummary(4.minutes)
                                ),
                                status = OrtRunStatus.FINISHED,
                                labels = mapOf("label key" to "label value"),
                                jobConfigContext = null,
                                resolvedJobConfigContext = "32f955941e94d0a318e1c985903f42af924e9050",
                                environmentConfigPath = null
                            )
                        ),
                        PagingData(
                            limit = 20,
                            offset = 0,
                            totalCount = 1,
                            sortProperties = listOf(SortProperty("createdAt", SortDirection.DESCENDING)),
                        ),
                        OrtRunFilters(
                            status = FilterOperatorAndValue(
                                ComparisonOperator.IN,
                                setOf(OrtRunStatus.FINISHED)
                            )
                        )
                    )
                }
            }
        }
    }
}

val getOrtRunStatistics: OpenApiRoute.() -> Unit = {
    operationId = "getOrtRunStatistics"
    summary = "Get statistics about an ORT run."
    tags = listOf("Runs")

    request {
        pathParameter<Long>("runId") {
            description = "The ID of the ORT run."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<OrtRunStatistics> {
                example("Get ORT run statistics") {
                    value = OrtRunStatistics(
                        issuesCount = 10,
                        issuesCountBySeverity = mapOf(
                            Severity.HINT to 4,
                            Severity.WARNING to 0,
                            Severity.ERROR to 6
                        ),
                        packagesCount = 200,
                        ecosystems = listOf(
                            EcosystemStats("Maven", 55),
                            EcosystemStats("NPM", 145)
                           ),
                        vulnerabilitiesCount = 3,
                        vulnerabilitiesCountByRating = mapOf(
                            VulnerabilityRating.NONE to 0,
                            VulnerabilityRating.LOW to 1,
                            VulnerabilityRating.MEDIUM to 0,
                            VulnerabilityRating.HIGH to 1,
                            VulnerabilityRating.CRITICAL to 1
                        ),
                        ruleViolationsCount = 5,
                        ruleViolationsCountBySeverity = mapOf(
                            Severity.HINT to 0,
                            Severity.WARNING to 1,
                            Severity.ERROR to 4
                        )
                    )
                }
            }
        }
    }
}
