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

import io.github.smiley4.ktoropenapi.config.RouteConfig

import io.ktor.http.HttpStatusCode

import kotlin.time.Duration.Companion.minutes

import org.eclipse.apoapsis.ortserver.api.v1.model.AdvisorDetails
import org.eclipse.apoapsis.ortserver.api.v1.model.ComparisonOperator
import org.eclipse.apoapsis.ortserver.api.v1.model.EcosystemStats
import org.eclipse.apoapsis.ortserver.api.v1.model.FilterOperatorAndValue
import org.eclipse.apoapsis.ortserver.api.v1.model.Identifier
import org.eclipse.apoapsis.ortserver.api.v1.model.Issue
import org.eclipse.apoapsis.ortserver.api.v1.model.JobSummaries
import org.eclipse.apoapsis.ortserver.api.v1.model.LicenseSource
import org.eclipse.apoapsis.ortserver.api.v1.model.Licenses
import org.eclipse.apoapsis.ortserver.api.v1.model.LogLevel
import org.eclipse.apoapsis.ortserver.api.v1.model.LogSource
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunFilters
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatistics
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunSummary
import org.eclipse.apoapsis.ortserver.api.v1.model.Package
import org.eclipse.apoapsis.ortserver.api.v1.model.PackageCuration
import org.eclipse.apoapsis.ortserver.api.v1.model.PackageCurationData
import org.eclipse.apoapsis.ortserver.api.v1.model.PackageFilters
import org.eclipse.apoapsis.ortserver.api.v1.model.ProcessedDeclaredLicense
import org.eclipse.apoapsis.ortserver.api.v1.model.Project
import org.eclipse.apoapsis.ortserver.api.v1.model.RemoteArtifact
import org.eclipse.apoapsis.ortserver.api.v1.model.RepositoryType
import org.eclipse.apoapsis.ortserver.api.v1.model.RuleViolation
import org.eclipse.apoapsis.ortserver.api.v1.model.RuleViolationResolution
import org.eclipse.apoapsis.ortserver.api.v1.model.RuleViolationResolutionReason
import org.eclipse.apoapsis.ortserver.api.v1.model.Severity
import org.eclipse.apoapsis.ortserver.api.v1.model.ShortestDependencyPath
import org.eclipse.apoapsis.ortserver.api.v1.model.UserDisplayName
import org.eclipse.apoapsis.ortserver.api.v1.model.VcsInfo
import org.eclipse.apoapsis.ortserver.api.v1.model.Vulnerability
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityRating
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityReference
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityResolution
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityResolutionReason
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityWithDetails
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedSearchResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagingData
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortProperty
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody
import org.eclipse.apoapsis.ortserver.shared.ktorutils.standardListQueryParameters

val getRun: RouteConfig.() -> Unit = {
    operationId = "getRun"
    summary = "Get details of an ORT run"
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
                        environmentConfigPath = null,
                        userDisplayName = UserDisplayName("john.doe", "John Doe")
                    )
                }
            }
        }
    }
}

val deleteRun: RouteConfig.() -> Unit = {
    operationId = "deleteRun"
    summary = "Delete an ORT run"
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

val getRunReport: RouteConfig.() -> Unit = {
    operationId = "getRunReport"
    summary = "Download a report of an ORT run"
    tags = listOf("Runs")

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

val getRunLogs: RouteConfig.() -> Unit = {
    operationId = "getRunLogs"
    summary = "Download an archive with selected logs of an ORT run"
    tags = listOf("Runs")

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

val getRunIssues: RouteConfig.() -> Unit = {
    operationId = "getRunIssues"
    summary = "Get the issues of an ORT run"
    tags = listOf("Runs")

    request {
        pathParameter<Long>("runId") {
            description = "The ID of the ORT run."
        }

        queryParameter<Boolean>("resolved") {
            description =
                """
                    If true, only resolved issues are returned. If false, only unresolved issues are returned.
                    If missing, both resolved and unresolved issues are returned.
                """.trimIndent()
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

val getRunVulnerabilities: RouteConfig.() -> Unit = {
    operationId = "getRunVulnerabilities"
    summary = "Get the vulnerabilities found in an ORT run"
    tags = listOf("Runs")

    request {
        pathParameter<Long>("runId") {
            description = "The ID of the ORT run."
        }

        queryParameter<Boolean>("resolved") {
            description =
                """
                    If true, only resolved vulnerabilities are returned. If false, only unresolved vulnerabilities are
                    returned. If missing, both resolved and unresolved vulnerabilities are returned.
                """.trimIndent()
        }

        standardListQueryParameters()
    }

    response {
        HttpStatusCode.OK to {
            description = "Success."
            jsonBody<PagedResponse<VulnerabilityWithDetails>> {
                example("Get vulnerabilities for an ORT run") {
                    value = PagedResponse(
                        listOf(
                            VulnerabilityWithDetails(
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
                                rating = VulnerabilityRating.HIGH,
                                listOf(
                                    VulnerabilityResolution(
                                        externalId = "CVE-2021-1234",
                                        reason = VulnerabilityResolutionReason.INEFFECTIVE_VULNERABILITY,
                                        comment = "A comment why the vulnerability can be resolved."
                                    )
                                ),
                                advisor = AdvisorDetails(name = "VulnerableCode"),
                                purl = "pkg:maven/org.namespace/name@1.0"
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

val getRunRuleViolations: RouteConfig.() -> Unit = {
    operationId = "getRunRuleViolations"
    summary = "Get the rule violations found in an ORT run"
    tags = listOf("Runs")

    request {
        pathParameter<Long>("runId") {
            description = "The ID of the ORT run."
        }

        queryParameter<Boolean>("resolved") {
            description =
                """
                    If true, only resolved rule violations are returned. If false, only unresolved rule violations are
                    returned. If missing, both resolved and unresolved rule violations are returned.
                """.trimIndent()
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
                                Identifier(
                                    "Maven",
                                    "org.glassfish.jersey.media",
                                    "jersey-media-jaxb",
                                    "2.42"
                                ),
                                "GPL-1.0-or-later",
                                LicenseSource.DETECTED,
                                setOf(LicenseSource.DECLARED, LicenseSource.DETECTED),
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
                                listOf(
                                    RuleViolationResolution(
                                        message =
                                            "The declared license '.*' could not be mapped to a valid SPDX expression.",
                                        reason = RuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                                        comment = "A comment why the rule violation can be resolved."
                                    )
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
                    description = """
                        A list of all rule violations found in the ORT run.
                        Note: ORT may now detect multiple license sources for a single rule violation. These are
                        available in the `licenseSources` property. For backward compatibility, there is also a
                        `licenseSource` property containing only the first license source if available. This is
                        deprecated, and clients should migrate to using the `licenseSources` property.
                    """.trimIndent()
                }
            }
        }

        HttpStatusCode.NotFound to {
            description = "The ORT run does not exist."
        }
    }
}

val getRunPackages: RouteConfig.() -> Unit = {
    operationId = "getRunPackages"
    summary = "Get the packages found in an ORT run"
    tags = listOf("Runs")

    request {
        pathParameter<Long>("runId") {
            description = "The ID of the ORT run."
        }

        queryParameter<String>("identifier") {
            description = "Defines an ORT package identifier for which the packages are to be retrieved. This uses a " +
                    "case-insensitive substring match."
        }

        queryParameter<String>("purl") {
            description = "Defines a purl for which the packages are to be retrieved. This uses a case-insensitive " +
                    "substring match."
        }

        queryParameter<String>("processedDeclaredLicense") {
            description = "Defines the processed declared licenses for which packages are to be retrieved. This is a " +
                    "comma-separated string. Add a minus as the first item to exclude packages with the specified " +
                    "license expressions, e.g. '-,MIT'."
        }

        standardListQueryParameters()
    }

    response {
        HttpStatusCode.OK to {
            description = "Success."
            jsonBody<PagedSearchResponse<Package, PackageFilters>> {
                example("Get packages for an ORT run") {
                    value = PagedSearchResponse(
                        listOf(
                            Package(
                                identifier = Identifier("Maven", "org.example", "name", "1.0"),
                                purl = "pkg:maven/org.example/name@1.0",
                                cpe = null,
                                authors = setOf("author1", "author2"),
                                declaredLicenses = setOf("license1", "license2"),
                                processedDeclaredLicense = ProcessedDeclaredLicense(
                                    spdxExpression = "Apache-2.0",
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
                                sourceCodeOrigins = null,
                                labels = emptyMap(),
                                shortestDependencyPaths = listOf(
                                    ShortestDependencyPath(
                                        scope = "productionRuntimeClasspath",
                                        projectIdentifier = Identifier("Gradle", "", "project-name", "1.0"),
                                        path = listOf(
                                            Identifier("Maven", "org.example", "some", "1.0"),
                                            Identifier("Maven", "org.example", "other", "1.0")
                                        )
                                    )
                                ),
                                curations = listOf(
                                    PackageCuration(
                                        providerName = "Example",
                                        data = PackageCurationData(
                                            comment = "Fix the homepage URL.",
                                            purl = null,
                                            cpe = null,
                                            authors = null,
                                            concludedLicense = null,
                                            description = null,
                                            homepageUrl = "https://example.org",
                                            binaryArtifact = null,
                                            sourceArtifact = null,
                                            vcs = null,
                                            isMetadataOnly = null,
                                            isModified = null,
                                            declaredLicenseMapping = emptyMap(),
                                            sourceCodeOrigins = null,
                                            labels = emptyMap()
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
                        ),
                        PackageFilters(
                            identifier = FilterOperatorAndValue(
                                operator = ComparisonOperator.ILIKE,
                                value = "Maven:org.example/name@1.0"
                            ),
                            purl = FilterOperatorAndValue(
                                operator = ComparisonOperator.ILIKE,
                                value = "pkg:maven/org.example/name@1.0"
                            ),
                            processedDeclaredLicense = FilterOperatorAndValue(
                                operator = ComparisonOperator.IN,
                                value = setOf("Apache-2.0")
                            )
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

val getRunProjects: RouteConfig.() -> Unit = {
    operationId = "getRunProjects"
    summary = "Get the projects found in an ORT run"
    tags = listOf("Runs")

    request {
        pathParameter<Long>("runId") {
            description = "The ID of the ORT run."
        }

        standardListQueryParameters()
    }

    response {
        HttpStatusCode.OK to {
            description = "Success."
            jsonBody<PagedResponse<Project>> {
                example("Get project for an ORT run") {
                    value = PagedResponse(
                        listOf(
                            Project(
                                identifier = Identifier("Maven", "org.namespace", "name", "1.0"),
                                cpe = null,
                                definitionFilePath = "path/to/definition",
                                authors = setOf("author1", "author2"),
                                declaredLicenses = setOf("license1", "license2"),
                                processedDeclaredLicense = ProcessedDeclaredLicense(
                                    spdxExpression = "Expression",
                                    mappedLicenses = emptyMap(),
                                    unmappedLicenses = emptySet()
                                ),
                                vcs = VcsInfo(RepositoryType.GIT.name, "url", "revision", "path"),
                                vcsProcessed = VcsInfo(RepositoryType.GIT.name, "url", "revision", "path"),
                                description = "A description",
                                homepageUrl = "https://example.com/namespace/name",
                                scopeNames = setOf("scope1", "scope2")
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

val getRuns: RouteConfig.() -> Unit = {
    operationId = "getRuns"
    summary = "Get all ORT runs"
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
                                createdAt = CREATED_AT,
                                finishedAt = FINISHED_AT,
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
                            sortProperties = listOf(SortProperty("createdAt", SortDirection.DESCENDING))
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

val getRunStatistics: RouteConfig.() -> Unit = {
    operationId = "getRunStatistics"
    summary = "Get statistics about an ORT run"
    description = """
        Returns statistics for issues, vulnerabilities, and rule violations found in the ORT run.
        Statistics distinguish between unresolved and total counts:
        - Unresolved counts (issuesCount, vulnerabilitiesCount, ruleViolationsCount) exclude items that have been
          marked as resolved via resolutions configured in the repository configuration.
        - Total counts (issuesCountTotal, vulnerabilitiesCountTotal, ruleViolationsCountTotal) include all items
          regardless of their resolved status.
    """.trimIndent()
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
                        issuesCount = 8,
                        issuesCountBySeverity = mapOf(
                            Severity.HINT to 3,
                            Severity.WARNING to 0,
                            Severity.ERROR to 5
                        ),
                        issuesCountTotal = 10,
                        issuesCountTotalBySeverity = mapOf(
                            Severity.HINT to 4,
                            Severity.WARNING to 0,
                            Severity.ERROR to 6
                        ),
                        packagesCount = 200,
                        ecosystems = listOf(
                            EcosystemStats("Maven", 55),
                            EcosystemStats("NPM", 145)
                           ),
                        vulnerabilitiesCount = 2,
                        vulnerabilitiesCountByRating = mapOf(
                            VulnerabilityRating.NONE to 0,
                            VulnerabilityRating.LOW to 1,
                            VulnerabilityRating.MEDIUM to 0,
                            VulnerabilityRating.HIGH to 1,
                            VulnerabilityRating.CRITICAL to 0
                        ),
                        vulnerabilitiesCountTotal = 3,
                        vulnerabilitiesCountTotalByRating = mapOf(
                            VulnerabilityRating.NONE to 0,
                            VulnerabilityRating.LOW to 1,
                            VulnerabilityRating.MEDIUM to 0,
                            VulnerabilityRating.HIGH to 1,
                            VulnerabilityRating.CRITICAL to 1
                        ),
                        ruleViolationsCount = 4,
                        ruleViolationsCountBySeverity = mapOf(
                            Severity.HINT to 0,
                            Severity.WARNING to 1,
                            Severity.ERROR to 3
                        ),
                        ruleViolationsCountTotal = 5,
                        ruleViolationsCountTotalBySeverity = mapOf(
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

val getRunPackageLicenses: RouteConfig.() -> Unit = {
    operationId = "getRunPackageLicenses"
    summary = "Get the licenses for packages found in an ORT run"
    tags = listOf("Runs")

    request {
        pathParameter<Long>("runId") {
            description = "The ID of the ORT run."
        }
    }

    response {
        HttpStatusCode.OK to {
            jsonBody<Licenses> {
                example("Get licenses for packages") {
                    value = Licenses(
                        processedDeclaredLicenses = listOf("Apache-2.0", "MIT")
                    )
                }
            }
        }
    }
}
