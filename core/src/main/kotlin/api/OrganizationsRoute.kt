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

import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.patch
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToApi
import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToModel
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateInfrastructureService
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateOrganization
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateProduct
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateSecret
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatistics
import org.eclipse.apoapsis.ortserver.api.v1.model.PagedResponse
import org.eclipse.apoapsis.ortserver.api.v1.model.SortDirection
import org.eclipse.apoapsis.ortserver.api.v1.model.SortProperty
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateInfrastructureService
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateOrganization
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateSecret
import org.eclipse.apoapsis.ortserver.api.v1.model.Username
import org.eclipse.apoapsis.ortserver.components.authorization.hasPermission
import org.eclipse.apoapsis.ortserver.components.authorization.permissions.OrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.requirePermission
import org.eclipse.apoapsis.ortserver.components.authorization.requireSuperuser
import org.eclipse.apoapsis.ortserver.core.api.UserWithGroupsHelper.mapToApi
import org.eclipse.apoapsis.ortserver.core.api.UserWithGroupsHelper.sortAndPage
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteInfrastructureServiceForOrganizationIdAndName
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteOrganizationById
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteSecretByOrganizationIdAndName
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteUserFromOrganizationGroup
import org.eclipse.apoapsis.ortserver.core.apiDocs.getInfrastructureServicesByOrganizationId
import org.eclipse.apoapsis.ortserver.core.apiDocs.getOrganizationById
import org.eclipse.apoapsis.ortserver.core.apiDocs.getOrganizationProducts
import org.eclipse.apoapsis.ortserver.core.apiDocs.getOrganizations
import org.eclipse.apoapsis.ortserver.core.apiDocs.getOrtRunStatisticsByOrganizationId
import org.eclipse.apoapsis.ortserver.core.apiDocs.getSecretByOrganizationIdAndName
import org.eclipse.apoapsis.ortserver.core.apiDocs.getSecretsByOrganizationId
import org.eclipse.apoapsis.ortserver.core.apiDocs.getUsersForOrganization
import org.eclipse.apoapsis.ortserver.core.apiDocs.getVulnerabilitiesAcrossRepositoriesByOrganizationId
import org.eclipse.apoapsis.ortserver.core.apiDocs.patchInfrastructureServiceForOrganizationIdAndName
import org.eclipse.apoapsis.ortserver.core.apiDocs.patchOrganizationById
import org.eclipse.apoapsis.ortserver.core.apiDocs.patchSecretByOrganizationIdAndName
import org.eclipse.apoapsis.ortserver.core.apiDocs.postInfrastructureServiceForOrganization
import org.eclipse.apoapsis.ortserver.core.apiDocs.postOrganizations
import org.eclipse.apoapsis.ortserver.core.apiDocs.postProduct
import org.eclipse.apoapsis.ortserver.core.apiDocs.postSecretForOrganization
import org.eclipse.apoapsis.ortserver.core.apiDocs.putUserToOrganizationGroup
import org.eclipse.apoapsis.ortserver.core.utils.paginate
import org.eclipse.apoapsis.ortserver.core.utils.pagingOptions
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.Product
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.VulnerabilityWithAccumulatedData
import org.eclipse.apoapsis.ortserver.services.InfrastructureServiceService
import org.eclipse.apoapsis.ortserver.services.IssueService
import org.eclipse.apoapsis.ortserver.services.OrganizationService
import org.eclipse.apoapsis.ortserver.services.PackageService
import org.eclipse.apoapsis.ortserver.services.RepositoryService
import org.eclipse.apoapsis.ortserver.services.RuleViolationService
import org.eclipse.apoapsis.ortserver.services.SecretService
import org.eclipse.apoapsis.ortserver.services.UserService
import org.eclipse.apoapsis.ortserver.services.VulnerabilityService
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireParameter

@Suppress("LongMethod")
fun Route.organizations(
    infrastructureServiceService: InfrastructureServiceService,
    issueService: IssueService,
    organizationService: OrganizationService,
    packageService: PackageService,
    repositoryService: RepositoryService,
    ruleViolationService: RuleViolationService,
    secretService: SecretService,
    userService: UserService,
    vulnerabilityService: VulnerabilityService
) = route("organizations") {
    get(getOrganizations) {
        val pagingOptions = call.pagingOptions(SortProperty("name", SortDirection.ASCENDING))

        val filteredOrganizations = organizationService
            .listOrganizations(pagingOptions.copy(limit = null, offset = null).mapToModel())
            .filter { hasPermission(it.id, OrganizationPermission.READ) }

        val pagedOrganizations = filteredOrganizations.paginate(pagingOptions)
            .map { it.mapToApi() }

        val pagedResponse = PagedResponse(
            pagedOrganizations,
            pagingOptions.toPagingData(filteredOrganizations.size.toLong())
        )

        call.respond(HttpStatusCode.OK, pagedResponse)
    }

    post(postOrganizations) {
        requireSuperuser()

        val createOrganization = call.receive<CreateOrganization>()

        val createdOrganization =
            organizationService.createOrganization(createOrganization.name, createOrganization.description)

        call.respond(HttpStatusCode.Created, createdOrganization.mapToApi())
    }

    route("{organizationId}") {
        get(getOrganizationById) {
            requirePermission(OrganizationPermission.READ)

            val id = call.requireIdParameter("organizationId")

            val organization = organizationService.getOrganization(id)

            organization?.let { call.respond(HttpStatusCode.OK, it.mapToApi()) }
                ?: call.respond(HttpStatusCode.NotFound)
        }

        patch(patchOrganizationById) {
            requirePermission(OrganizationPermission.WRITE)

            val organizationId = call.requireIdParameter("organizationId")
            val org = call.receive<UpdateOrganization>()

            val updatedOrg = organizationService.updateOrganization(
                organizationId,
                org.name.mapToModel(),
                org.description.mapToModel()
            )

            call.respond(HttpStatusCode.OK, updatedOrg.mapToApi())
        }

        delete(deleteOrganizationById) {
            requirePermission(OrganizationPermission.DELETE)

            val id = call.requireIdParameter("organizationId")

            organizationService.deleteOrganization(id)

            call.respond(HttpStatusCode.NoContent)
        }

        route("products") {
            get(getOrganizationProducts) {
                requirePermission(OrganizationPermission.READ_PRODUCTS)

                val orgId = call.requireIdParameter("organizationId")
                val pagingOptions = call.pagingOptions(SortProperty("name", SortDirection.ASCENDING))

                val productsForOrganization =
                    organizationService.listProductsForOrganization(orgId, pagingOptions.mapToModel())

                val pagedResponse = productsForOrganization.mapToApi(Product::mapToApi)

                call.respond(HttpStatusCode.OK, pagedResponse)
            }

            post(postProduct) {
                requirePermission(OrganizationPermission.CREATE_PRODUCT)

                val createProduct = call.receive<CreateProduct>()
                val orgId = call.requireIdParameter("organizationId")

                val createdProduct =
                    organizationService.createProduct(createProduct.name, createProduct.description, orgId)

                call.respond(HttpStatusCode.Created, createdProduct.mapToApi())
            }
        }

        route("secrets") {
            get(getSecretsByOrganizationId) {
                requirePermission(OrganizationPermission.READ)

                val orgId = call.requireIdParameter("organizationId")
                val pagingOptions = call.pagingOptions(SortProperty("name", SortDirection.ASCENDING))

                val secretsForOrganization = secretService.listForOrganization(orgId, pagingOptions.mapToModel())

                val pagedResponse = secretsForOrganization.mapToApi(Secret::mapToApi)

                call.respond(HttpStatusCode.OK, pagedResponse)
            }

            route("{secretName}") {
                get(getSecretByOrganizationIdAndName) {
                    requirePermission(OrganizationPermission.READ)

                    val organizationId = call.requireIdParameter("organizationId")
                    val secretName = call.requireParameter("secretName")

                    secretService.getSecretByOrganizationIdAndName(organizationId, secretName)
                        ?.let { call.respond(HttpStatusCode.OK, it.mapToApi()) }
                        ?: call.respond(HttpStatusCode.NotFound)
                }

                patch(patchSecretByOrganizationIdAndName) {
                    requirePermission(OrganizationPermission.WRITE_SECRETS)

                    val organizationId = call.requireIdParameter("organizationId")
                    val secretName = call.requireParameter("secretName")
                    val updateSecret = call.receive<UpdateSecret>()

                    call.respond(
                        HttpStatusCode.OK,
                        secretService.updateSecretByOrganizationAndName(
                            organizationId,
                            secretName,
                            updateSecret.value.mapToModel(),
                            updateSecret.description.mapToModel()
                        ).mapToApi()
                    )
                }

                delete(deleteSecretByOrganizationIdAndName) {
                    requirePermission(OrganizationPermission.WRITE_SECRETS)

                    val organizationId = call.requireIdParameter("organizationId")
                    val secretName = call.requireParameter("secretName")

                    secretService.deleteSecretByOrganizationAndName(organizationId, secretName)

                    call.respond(HttpStatusCode.NoContent)
                }
            }

            post(postSecretForOrganization) {
                requirePermission(OrganizationPermission.WRITE_SECRETS)

                val organizationId = call.requireIdParameter("organizationId")
                val createSecret = call.receive<CreateSecret>()

                call.respond(
                    HttpStatusCode.Created,
                    secretService.createSecret(
                        createSecret.name,
                        createSecret.value,
                        createSecret.description,
                        organizationId,
                        null,
                        null
                    ).mapToApi()
                )
            }
        }

        route("infrastructure-services") {
            get(getInfrastructureServicesByOrganizationId) {
                requirePermission(OrganizationPermission.READ)

                val orgId = call.requireIdParameter("organizationId")
                val pagingOptions = call.pagingOptions(SortProperty("name", SortDirection.ASCENDING))

                val infrastructureServicesForOrganization =
                    infrastructureServiceService.listForOrganization(orgId, pagingOptions.mapToModel())

                val pagedResponse = infrastructureServicesForOrganization.mapToApi(InfrastructureService::mapToApi)

                call.respond(HttpStatusCode.OK, pagedResponse)
            }

            post(postInfrastructureServiceForOrganization) {
                requirePermission(OrganizationPermission.WRITE)

                val organizationId = call.requireIdParameter("organizationId")
                val createService = call.receive<CreateInfrastructureService>()

                val newService = infrastructureServiceService.createForOrganization(
                    organizationId,
                    createService.name,
                    createService.url,
                    createService.description,
                    createService.usernameSecretRef,
                    createService.passwordSecretRef,
                    createService.credentialsTypes.mapToModel()
                )

                call.respond(HttpStatusCode.Created, newService.mapToApi())
            }

            route("{serviceName}") {
                patch(patchInfrastructureServiceForOrganizationIdAndName) {
                    requirePermission(OrganizationPermission.WRITE)

                    val organizationId = call.requireIdParameter("organizationId")
                    val serviceName = call.requireParameter("serviceName")
                    val updateService = call.receive<UpdateInfrastructureService>()

                    val updatedService = infrastructureServiceService.updateForOrganization(
                        organizationId,
                        serviceName,
                        updateService.url.mapToModel(),
                        updateService.description.mapToModel(),
                        updateService.usernameSecretRef.mapToModel(),
                        updateService.passwordSecretRef.mapToModel(),
                        updateService.credentialsTypes.mapToModel { type -> type.mapToModel() }
                    )

                    call.respond(HttpStatusCode.OK, updatedService.mapToApi())
                }

                delete(deleteInfrastructureServiceForOrganizationIdAndName) {
                    requirePermission(OrganizationPermission.WRITE)

                    val organizationId = call.requireIdParameter("organizationId")
                    val serviceName = call.requireParameter("serviceName")

                    infrastructureServiceService.deleteForOrganization(organizationId, serviceName)

                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }

        route("groups") {
            // Instead of identifying arbitrary groups with a groupId, there are only 3 groups with fixed
            // groupId "readers", "writers" or "admins".
            route("{groupId}") {
                put(putUserToOrganizationGroup) {
                    requirePermission(OrganizationPermission.MANAGE_GROUPS)

                    val user = call.receive<Username>()
                    val organizationId = call.requireIdParameter("organizationId")
                    val groupId = call.requireParameter("groupId")

                    organizationService.addUserToGroup(user.username, organizationId, groupId)
                    call.respond(HttpStatusCode.NoContent)
                }

                delete(deleteUserFromOrganizationGroup) {
                    requirePermission(OrganizationPermission.MANAGE_GROUPS)

                    val user = call.receive<Username>()
                    val organizationId = call.requireIdParameter("organizationId")
                    val groupId = call.requireParameter("groupId")

                    organizationService.removeUserFromGroup(user.username, organizationId, groupId)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }

        route("vulnerabilities") {
            get(getVulnerabilitiesAcrossRepositoriesByOrganizationId) {
                requirePermission(OrganizationPermission.READ)

                val organizationId = call.requireIdParameter("organizationId")
                val pagingOptions = call.pagingOptions(SortProperty("rating", SortDirection.DESCENDING))

                val repositoryIds = organizationService.getRepositoryIdsForOrganization(organizationId)

                val ortRunIds = repositoryIds.mapNotNull { repositoryId ->
                    repositoryService.getLatestOrtRunIdWithSuccessfulAdvisorJob(repositoryId)
                }

                val vulnerabilities =
                    vulnerabilityService.listForOrtRuns(ortRunIds, pagingOptions.mapToModel())

                val pagedResponse = vulnerabilities.mapToApi(VulnerabilityWithAccumulatedData::mapToApi)

                call.respond(HttpStatusCode.OK, pagedResponse)
            }
        }

        route("statistics") {
            route("runs") {
                get(getOrtRunStatisticsByOrganizationId) {
                    requirePermission(OrganizationPermission.READ)

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
            get(getUsersForOrganization) {
                requirePermission(OrganizationPermission.READ)

                val orgId = call.requireIdParameter("organizationId")
                val pagingOptions = call.pagingOptions(SortProperty("username", SortDirection.ASCENDING))

                val users = userService.getUsersHavingRightsForOrganization(orgId).mapToApi()

                call.respond(
                    PagedResponse(users.sortAndPage(pagingOptions), pagingOptions.toPagingData(users.size.toLong()))
                )
            }
        }
    }
}
