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

package org.eclipse.apoapsis.ortserver.core.api

import io.github.smiley4.ktoropenapi.get

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToApi
import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToModel
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatistics
import org.eclipse.apoapsis.ortserver.api.v1.model.PatchOrganization
import org.eclipse.apoapsis.ortserver.api.v1.model.PostOrganization
import org.eclipse.apoapsis.ortserver.api.v1.model.PostProduct
import org.eclipse.apoapsis.ortserver.api.v1.model.Username
import org.eclipse.apoapsis.ortserver.components.authorization.api.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.routes.OrtServerPrincipal
import org.eclipse.apoapsis.ortserver.components.authorization.routes.OrtServerPrincipal.Companion.requirePrincipal
import org.eclipse.apoapsis.ortserver.components.authorization.routes.delete
import org.eclipse.apoapsis.ortserver.components.authorization.routes.get
import org.eclipse.apoapsis.ortserver.components.authorization.routes.mapToModel
import org.eclipse.apoapsis.ortserver.components.authorization.routes.patch
import org.eclipse.apoapsis.ortserver.components.authorization.routes.post
import org.eclipse.apoapsis.ortserver.components.authorization.routes.put
import org.eclipse.apoapsis.ortserver.components.authorization.routes.requirePermission
import org.eclipse.apoapsis.ortserver.components.authorization.routes.requireSuperuser
import org.eclipse.apoapsis.ortserver.components.authorization.service.AuthorizationService
import org.eclipse.apoapsis.ortserver.components.authorization.service.UserService
import org.eclipse.apoapsis.ortserver.core.api.UserWithGroupsHelper.mapToApi
import org.eclipse.apoapsis.ortserver.core.api.UserWithGroupsHelper.sortAndPage
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteOrganization
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteOrganizationRoleFromUser
import org.eclipse.apoapsis.ortserver.core.apiDocs.getOrganization
import org.eclipse.apoapsis.ortserver.core.apiDocs.getOrganizationProducts
import org.eclipse.apoapsis.ortserver.core.apiDocs.getOrganizationRunStatistics
import org.eclipse.apoapsis.ortserver.core.apiDocs.getOrganizationUsers
import org.eclipse.apoapsis.ortserver.core.apiDocs.getOrganizationVulnerabilities
import org.eclipse.apoapsis.ortserver.core.apiDocs.getOrganizations
import org.eclipse.apoapsis.ortserver.core.apiDocs.patchOrganization
import org.eclipse.apoapsis.ortserver.core.apiDocs.postOrganization
import org.eclipse.apoapsis.ortserver.core.apiDocs.postProduct
import org.eclipse.apoapsis.ortserver.core.apiDocs.putOrganizationRoleToUser
import org.eclipse.apoapsis.ortserver.core.utils.vulnerabilityForRunsFilters
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.Product
import org.eclipse.apoapsis.ortserver.model.VulnerabilityWithAccumulatedData
import org.eclipse.apoapsis.ortserver.services.OrganizationService
import org.eclipse.apoapsis.ortserver.services.RepositoryService
import org.eclipse.apoapsis.ortserver.services.ortrun.IssueService
import org.eclipse.apoapsis.ortserver.services.ortrun.PackageService
import org.eclipse.apoapsis.ortserver.services.ortrun.RuleViolationService
import org.eclipse.apoapsis.ortserver.services.ortrun.VulnerabilityService
import org.eclipse.apoapsis.ortserver.shared.apimappings.mapToApi
import org.eclipse.apoapsis.ortserver.shared.apimappings.mapToModel
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortProperty
import org.eclipse.apoapsis.ortserver.shared.ktorutils.filterParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.paginate
import org.eclipse.apoapsis.ortserver.shared.ktorutils.pagingOptions
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireEnumParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.respondError

import org.koin.ktor.ext.inject

@Suppress("LongMethod")
fun Route.organizations() = route("organizations") {
    val authorizationService by inject<AuthorizationService>()
    val organizationService by inject<OrganizationService>()
    val repositoryService by inject<RepositoryService>()
    val vulnerabilityService by inject<VulnerabilityService>()
    val issueService by inject<IssueService>()
    val ruleViolationService by inject<RuleViolationService>()
    val packageService by inject<PackageService>()
    val userService by inject<UserService>()

    get(getOrganizations) {
        val pagingOptions = call.pagingOptions(SortProperty("name", SortDirection.ASCENDING))
        val filter = call.filterParameter("filter")
        val principal = requireNotNull(call.principal<OrtServerPrincipal>())

        val filteredOrganizations = organizationService
            .listOrganizationsForUser(
                principal.username,
                parameters = pagingOptions.copy(limit = null, offset = null).mapToModel(),
                filter = filter?.mapToModel()
            ).data

        val pagedOrganizations = filteredOrganizations.paginate(pagingOptions)
            .map { it.mapToApi() }

        val pagedResponse = PagedResponse(
            pagedOrganizations,
            pagingOptions.toPagingData(filteredOrganizations.size.toLong())
        )

        call.respond(HttpStatusCode.OK, pagedResponse)
    }

    post(postOrganization, requireSuperuser()) {
        val createOrganization = call.receive<PostOrganization>()

        val createdOrganization =
            organizationService.createOrganization(createOrganization.name, createOrganization.description)

        call.respond(HttpStatusCode.Created, createdOrganization.mapToApi())
    }

    route("{organizationId}") {
        get(getOrganization, requirePermission(OrganizationPermission.READ)) {
            val id = call.requireIdParameter("organizationId")

            val organization = organizationService.getOrganization(id)

            organization?.let { call.respond(HttpStatusCode.OK, it.mapToApi()) }
                ?: call.respond(HttpStatusCode.NotFound)
        }

        patch(patchOrganization, requirePermission(OrganizationPermission.WRITE)) {
            val organizationId = call.requireIdParameter("organizationId")
            val org = call.receive<PatchOrganization>()

            val updatedOrg = organizationService.updateOrganization(
                organizationId,
                org.name.mapToModel(),
                org.description.mapToModel()
            )

            call.respond(HttpStatusCode.OK, updatedOrg.mapToApi())
        }

        delete(deleteOrganization, requirePermission(OrganizationPermission.DELETE)) {
            val id = call.requireIdParameter("organizationId")

            organizationService.deleteOrganization(id)

            call.respond(HttpStatusCode.NoContent)
        }

        route("products") {
            get(getOrganizationProducts, requirePermission(OrganizationPermission.READ_PRODUCTS)) {
                val orgId = call.requireIdParameter("organizationId")
                val pagingOptions = call.pagingOptions(SortProperty("name", SortDirection.ASCENDING))
                val filter = call.filterParameter("filter")
                val principal = requirePrincipal()

                val productsForOrganization =
                    organizationService.listProductsForOrganizationAndUser(
                        orgId,
                        principal.username,
                        pagingOptions.mapToModel(),
                        filter?.mapToModel()
                    )

                val pagedResponse = productsForOrganization.mapToApi(Product::mapToApi)

                call.respond(HttpStatusCode.OK, pagedResponse)
            }

            post(postProduct, requirePermission(OrganizationPermission.CREATE_PRODUCT)) {
                val createProduct = call.receive<PostProduct>()
                val orgId = call.requireIdParameter("organizationId")

                val createdProduct =
                    organizationService.createProduct(createProduct.name, createProduct.description, orgId)

                call.respond(HttpStatusCode.Created, createdProduct.mapToApi())
            }
        }

        route("roles") {
            route("{role}") {
                put(putOrganizationRoleToUser, requirePermission(OrganizationPermission.MANAGE_GROUPS)) {
                    val user = call.receive<Username>()
                    val organizationId = call.requireIdParameter("organizationId")
                    val role = call.requireEnumParameter<OrganizationRole>("role").mapToModel()

                    if (organizationService.getOrganization(organizationId) == null) {
                        call.respondError(HttpStatusCode.NotFound, "Organization with ID '$organizationId' not found.")
                        return@put
                    }

                    if (!userService.userExists(user.username)) {
                        call.respondError(
                            HttpStatusCode.NotFound,
                            "Could not find user with username '${user.username}'."
                        )
                    } else {
                        authorizationService.assignRole(
                            user.username,
                            role,
                            CompoundHierarchyId.forOrganization(OrganizationId(organizationId))
                        )
                        call.respond(HttpStatusCode.NoContent)
                    }
                }

                delete(deleteOrganizationRoleFromUser, requirePermission(OrganizationPermission.MANAGE_GROUPS)) {
                    val organizationId = call.requireIdParameter("organizationId")
                    val username = call.requireParameter("username")
                    call.requireEnumParameter<OrganizationRole>("role")

                    if (organizationService.getOrganization(organizationId) == null) {
                        call.respondError(HttpStatusCode.NotFound, "Organization with ID '$organizationId' not found.")
                        return@delete
                    }

                    if (!userService.userExists(username)) {
                        call.respondError(HttpStatusCode.NotFound, "Could not find user with username '$username'.")
                    } else {
                        authorizationService.removeAssignment(
                            username,
                            CompoundHierarchyId.forOrganization(OrganizationId(organizationId))
                        )
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }
        }

        route("vulnerabilities") {
            get(getOrganizationVulnerabilities, requirePermission(OrganizationPermission.READ)) {
                val organizationId = call.requireIdParameter("organizationId")
                val pagingOptions = call.pagingOptions(SortProperty("rating", SortDirection.DESCENDING))
                val filters = call.vulnerabilityForRunsFilters()

                val repositoryIds = organizationService.getRepositoryIdsForOrganization(organizationId)

                val ortRunIds = repositoryIds.mapNotNull { repositoryId ->
                    repositoryService.getLatestOrtRunIdWithSuccessfulAdvisorJob(repositoryId)
                }

                val vulnerabilities = vulnerabilityService.listForOrtRuns(
                    ortRunIds,
                    pagingOptions.mapToModel(),
                    filters.mapToModel()
                )

                val pagedSearchResponse = vulnerabilities
                    .mapToApi(VulnerabilityWithAccumulatedData::mapToApi)
                    .toSearchResponse(filters)

                call.respond(HttpStatusCode.OK, pagedSearchResponse)
            }
        }

        route("statistics") {
            route("runs") {
                get(getOrganizationRunStatistics, requirePermission(OrganizationPermission.READ)) {
                    val orgId = call.requireIdParameter("organizationId")

                    val repositoryIds = organizationService.getRepositoryIdsForOrganization(orgId)

                    val latestRunsWithAnalyzerJobInFinalState = repositoryIds.mapNotNull {
                        repositoryService.getLatestOrtRunIdWithAnalyzerJobInFinalState(it)
                    }.toLongArray()

                    val issuesCount = if (latestRunsWithAnalyzerJobInFinalState.isNotEmpty()) {
                        issueService.countForOrtRunIds(*latestRunsWithAnalyzerJobInFinalState)
                    } else {
                        null
                    }

                    val issuesBySeverity = if (latestRunsWithAnalyzerJobInFinalState.isNotEmpty()) {
                        issueService
                            .countBySeverityForOrtRunIds(*latestRunsWithAnalyzerJobInFinalState)
                            .map
                            .mapKeys { it.key.mapToApi() }
                    } else {
                        null
                    }

                    val latestRunsWithSuccessfulAnalyzerJob = repositoryIds.mapNotNull {
                        repositoryService.getLatestOrtRunIdWithSuccessfulAnalyzerJob(it)
                    }.toLongArray()

                    val packagesCount = if (latestRunsWithSuccessfulAnalyzerJob.isNotEmpty()) {
                        packageService.countForOrtRunIds(*latestRunsWithSuccessfulAnalyzerJob)
                    } else {
                        null
                    }

                    val ecosystems = if (latestRunsWithSuccessfulAnalyzerJob.isNotEmpty()) {
                        packageService.countEcosystemsForOrtRunIds(*latestRunsWithSuccessfulAnalyzerJob)
                            .map { it.mapToApi() }
                    } else {
                        null
                    }

                    val latestRunsWithSuccessfulAdvisorJob = repositoryIds.mapNotNull {
                        repositoryService.getLatestOrtRunIdWithSuccessfulAdvisorJob(it)
                    }.toLongArray()

                    val vulnerabilitiesCount = if (latestRunsWithSuccessfulAdvisorJob.isNotEmpty()) {
                        vulnerabilityService.countForOrtRunIds(*latestRunsWithSuccessfulAdvisorJob)
                    } else {
                        null
                    }

                    val vulnerabilitiesByRating = if (latestRunsWithSuccessfulAdvisorJob.isNotEmpty()) {
                        vulnerabilityService
                            .countByRatingForOrtRunIds(*latestRunsWithSuccessfulAdvisorJob)
                            .map
                            .mapKeys { it.key.mapToApi() }
                    } else {
                        null
                    }

                    val latestRunsWithSuccessfulEvaluatorJob = repositoryIds.mapNotNull {
                        repositoryService.getLatestOrtRunIdWithSuccessfulEvaluatorJob(it)
                    }.toLongArray()

                    val ruleViolationsCount = if (latestRunsWithSuccessfulEvaluatorJob.isNotEmpty()) {
                        ruleViolationService.countForOrtRunIds(*latestRunsWithSuccessfulEvaluatorJob)
                    } else {
                        null
                    }

                    val ruleViolationsBySeverity = if (latestRunsWithSuccessfulEvaluatorJob.isNotEmpty()) {
                        ruleViolationService
                            .countBySeverityForOrtRunIds(*latestRunsWithSuccessfulEvaluatorJob)
                            .map
                            .mapKeys { it.key.mapToApi() }
                    } else {
                        null
                    }

                    call.respond(
                        HttpStatusCode.OK,
                        OrtRunStatistics(
                            issuesCount = issuesCount,
                            issuesCountBySeverity = issuesBySeverity,
                            packagesCount = packagesCount,
                            ecosystems = ecosystems,
                            vulnerabilitiesCount = vulnerabilitiesCount,
                            vulnerabilitiesCountByRating = vulnerabilitiesByRating,
                            ruleViolationsCount = ruleViolationsCount,
                            ruleViolationsCountBySeverity = ruleViolationsBySeverity
                        )
                    )
                }
            }
        }

        route("users") {
            get(getOrganizationUsers, requirePermission(OrganizationPermission.READ)) {
                val orgId = CompoundHierarchyId.forOrganization(
                    OrganizationId(call.requireIdParameter("organizationId"))
                )
                val pagingOptions = call.pagingOptions(SortProperty("username", SortDirection.ASCENDING))

                val users = authorizationService.listUsers(orgId)
                    .mapToApi(userService) { it.assignedAt.level == CompoundHierarchyId.ORGANIZATION_LEVEL }
                call.respond(
                    PagedResponse(users.sortAndPage(pagingOptions), pagingOptions.toPagingData(users.size.toLong()))
                )
            }
        }
    }
}
