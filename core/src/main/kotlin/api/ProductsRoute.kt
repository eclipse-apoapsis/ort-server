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
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToApi
import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToModel
import org.eclipse.apoapsis.ortserver.api.v1.model.Jobs
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatistics
import org.eclipse.apoapsis.ortserver.api.v1.model.PatchProduct
import org.eclipse.apoapsis.ortserver.api.v1.model.PostRepository
import org.eclipse.apoapsis.ortserver.api.v1.model.PostRepositoryRun
import org.eclipse.apoapsis.ortserver.api.v1.model.Username
import org.eclipse.apoapsis.ortserver.components.authorization.OrtPrincipal
import org.eclipse.apoapsis.ortserver.components.authorization.api.ProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.getFullName
import org.eclipse.apoapsis.ortserver.components.authorization.getUserId
import org.eclipse.apoapsis.ortserver.components.authorization.getUsername
import org.eclipse.apoapsis.ortserver.components.authorization.hasRole
import org.eclipse.apoapsis.ortserver.components.authorization.mapToModel
import org.eclipse.apoapsis.ortserver.components.authorization.permissions.ProductPermission
import org.eclipse.apoapsis.ortserver.components.authorization.requirePermission
import org.eclipse.apoapsis.ortserver.components.authorization.roles.Superuser
import org.eclipse.apoapsis.ortserver.components.authorization.service.AuthorizationService
import org.eclipse.apoapsis.ortserver.components.authorization.service.UserService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateService
import org.eclipse.apoapsis.ortserver.core.api.UserWithGroupsHelper.mapToApi
import org.eclipse.apoapsis.ortserver.core.api.UserWithGroupsHelper.sortAndPage
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteProduct
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteProductRoleFromUser
import org.eclipse.apoapsis.ortserver.core.apiDocs.getProduct
import org.eclipse.apoapsis.ortserver.core.apiDocs.getProductRepositories
import org.eclipse.apoapsis.ortserver.core.apiDocs.getProductRunStatistics
import org.eclipse.apoapsis.ortserver.core.apiDocs.getProductUsers
import org.eclipse.apoapsis.ortserver.core.apiDocs.getProductVulnerabilities
import org.eclipse.apoapsis.ortserver.core.apiDocs.patchProduct
import org.eclipse.apoapsis.ortserver.core.apiDocs.postProductRuns
import org.eclipse.apoapsis.ortserver.core.apiDocs.postRepository
import org.eclipse.apoapsis.ortserver.core.apiDocs.putProductRoleToUser
import org.eclipse.apoapsis.ortserver.core.services.OrchestratorService
import org.eclipse.apoapsis.ortserver.core.utils.getPluginConfigs
import org.eclipse.apoapsis.ortserver.core.utils.hasKeepAliveWorkerFlag
import org.eclipse.apoapsis.ortserver.core.utils.vulnerabilityForRunsFilters
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.UserDisplayName
import org.eclipse.apoapsis.ortserver.model.VulnerabilityWithAccumulatedData
import org.eclipse.apoapsis.ortserver.services.ProductService
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
import org.eclipse.apoapsis.ortserver.shared.ktorutils.pagingOptions
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireEnumParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.respondError

import org.koin.ktor.ext.inject

@Suppress("LongMethod")
fun Route.products() = route("products/{productId}") {
    val authorizationService by inject<AuthorizationService>()
    val productService by inject<ProductService>()
    val pluginTemplateService by inject<PluginTemplateService>()
    val repositoryService by inject<RepositoryService>()
    val vulnerabilityService by inject<VulnerabilityService>()
    val issueService by inject<IssueService>()
    val ruleViolationService by inject<RuleViolationService>()
    val packageService by inject<PackageService>()
    val userService by inject<UserService>()
    val orchestratorService by inject<OrchestratorService>()

    get(getProduct) {
        requirePermission(ProductPermission.READ)

        val id = call.requireIdParameter("productId")

        val product = productService.getProduct(id)

        if (product != null) {
            call.respond(HttpStatusCode.OK, product.mapToApi())
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    patch(patchProduct) {
        requirePermission(ProductPermission.WRITE)

        val id = call.requireIdParameter("productId")
        val updateProduct = call.receive<PatchProduct>()

        val updatedProduct =
            productService.updateProduct(id, updateProduct.name.mapToModel(), updateProduct.description.mapToModel())

        call.respond(HttpStatusCode.OK, updatedProduct.mapToApi())
    }

    delete(deleteProduct) {
        requirePermission(ProductPermission.DELETE)

        val id = call.requireIdParameter("productId")

        productService.deleteProduct(id)

        call.respond(HttpStatusCode.NoContent)
    }

    route("repositories") {
        get(getProductRepositories) {
            requirePermission(ProductPermission.READ_REPOSITORIES)
            val filter = call.filterParameter("filter")

            val productId = call.requireIdParameter("productId")
            val pagingOptions = call.pagingOptions(SortProperty("url", SortDirection.ASCENDING))

            val repositoriesForProduct =
                productService.listRepositoriesForProduct(productId, pagingOptions.mapToModel(), filter?.mapToModel())

            val pagedResponse = repositoriesForProduct.mapToApi(Repository::mapToApi)

            call.respond(HttpStatusCode.OK, pagedResponse)
        }

        post(postRepository) {
            requirePermission(ProductPermission.CREATE_REPOSITORY)

            val id = call.requireIdParameter("productId")
            val createRepository = call.receive<PostRepository>()
            val repository = productService.createRepository(
                createRepository.type.mapToModel(),
                createRepository.url,
                id,
                createRepository.description
            ).mapToApi()

            call.respond(
                HttpStatusCode.Created,
                repository
            )
        }
    }

    route("roles") {
        route("{role}") {
            put(putProductRoleToUser) {
                requirePermission(ProductPermission.MANAGE_GROUPS)

                val user = call.receive<Username>()
                val productId = call.requireIdParameter("productId")
                val role = call.requireEnumParameter<ProductRole>("role").mapToModel()

                if (productService.getProduct(productId) == null) {
                    call.respondError(HttpStatusCode.NotFound, "Product with ID '$productId' not found.")
                    return@put
                }

                authorizationService.addUserRole(user.username, ProductId(productId), role)
                call.respond(HttpStatusCode.NoContent)
            }

            delete(deleteProductRoleFromUser) {
                requirePermission(ProductPermission.MANAGE_GROUPS)

                val productId = call.requireIdParameter("productId")
                val role = call.requireEnumParameter<ProductRole>("role").mapToModel()
                val username = call.requireParameter("username")

                if (productService.getProduct(productId) == null) {
                    call.respondError(HttpStatusCode.NotFound, "Product with ID '$productId' not found.")
                    return@delete
                }

                authorizationService.removeUserRole(username, ProductId(productId), role)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }

    route("vulnerabilities") {
        get(getProductVulnerabilities) {
            requirePermission(ProductPermission.READ)

            val productId = call.requireIdParameter("productId")
            val pagingOptions = call.pagingOptions(SortProperty("rating", SortDirection.DESCENDING))
            val filters = call.vulnerabilityForRunsFilters()

            val repositoryIds = productService.getRepositoryIdsForProduct(productId)

            val ortRunIds = repositoryIds.mapNotNull { repositoryId ->
                repositoryService.getLatestOrtRunIdWithSuccessfulAdvisorJob(repositoryId)
            }

            val vulnerabilities =
                vulnerabilityService.listForOrtRuns(ortRunIds, pagingOptions.mapToModel(), filters.mapToModel())

            val pagedSearchResponse = vulnerabilities
                .mapToApi(VulnerabilityWithAccumulatedData::mapToApi)
                .toSearchResponse(filters)

            call.respond(HttpStatusCode.OK, pagedSearchResponse)
        }
    }

    route("statistics") {
        route("runs") {
            get(getProductRunStatistics) {
                requirePermission(ProductPermission.READ)

                val productId = call.requireIdParameter("productId")

                val repositoryIds = productService.getRepositoryIdsForProduct(productId)

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

    route("runs") {
        post(postProductRuns) {
            requirePermission(ProductPermission.TRIGGER_ORT_RUN)

            val productId = call.requireIdParameter("productId")

            val product = productService.getProduct(productId)

            if (product == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            val createOrtRun = call.receive<PostRepositoryRun>()
            val userDisplayName = call.principal<OrtPrincipal>()?.let { principal ->
                UserDisplayName(principal.getUserId(), principal.getUsername(), principal.getFullName())
            }

            // Validate the plugin configuration.
            val validationResult = pluginTemplateService.validatePluginConfigs(
                pluginConfigs = createOrtRun.getPluginConfigs().mapValues { (_, pluginConfigs) ->
                    pluginConfigs.mapValues { (_, pluginConfig) -> pluginConfig.mapToModel() }
                },
                organizationId = product.organizationId
            )

            if (!validationResult.isValid) {
                call.respondError(
                    HttpStatusCode.BadRequest,
                    message = "Invalid plugin configuration.",
                    cause = validationResult.errors.joinToString(separator = "\n")
                )
                return@post
            }

            // Restrict the `keepAliveWorker` flags to superusers only.
            if (createOrtRun.hasKeepAliveWorkerFlag() && !hasRole(Superuser.ROLE_NAME)) {
                call.respondError(
                    HttpStatusCode.Forbidden,
                    "The 'keepAliveWorker' flag is only allowed for superusers."
                )
                return@post
            }

            val repositoryIds = when {
                createOrtRun.repositoryIds.isNotEmpty() -> {
                    val productRepoIds = productService.getRepositoryIdsForProduct(productId)
                    require(createOrtRun.repositoryIds.all { it in productRepoIds }) {
                        """
                        The following repository IDs do not belong to product $productId: 
                        ${createOrtRun.repositoryIds.filter { it !in productRepoIds }}
                        """.trimIndent()
                    }
                    createOrtRun.repositoryIds
                }

                createOrtRun.repositoryFailedIds.isNotEmpty() -> {
                    val productRepoIds = productService.getLatestOrtRunWithFailedStatusForProduct(productId)
                    val repoIdsNotInProduct = createOrtRun.repositoryFailedIds.filter { it !in productRepoIds }

                    if (repoIdsNotInProduct.isNotEmpty()) {
                        call.respondError(
                            HttpStatusCode.Conflict,
                            message = "The repositories do not have a latest ORT run with status FAILED for product " +
                                    "$productId.",
                            cause = "Invalid repository IDs: ${repoIdsNotInProduct.joinToString()}"
                        )
                        return@post
                    }
                    createOrtRun.repositoryFailedIds
                }

                else -> productService.getRepositoryIdsForProduct(productId)
            }

            val createdRuns = repositoryIds.map { repositoryId ->
                orchestratorService.createOrtRun(
                    repositoryId,
                    createOrtRun.revision,
                    createOrtRun.path,
                    createOrtRun.jobConfigs.mapToModel(),
                    createOrtRun.jobConfigContext,
                    createOrtRun.labels,
                    createOrtRun.environmentConfigPath,
                    userDisplayName
                ).mapToApi(Jobs())
            }

            call.respond(HttpStatusCode.Created, createdRuns)
        }
    }

    route("users") {
        get(getProductUsers) {
            requirePermission(ProductPermission.READ)

            val productId = call.requireIdParameter("productId")
            val pagingOptions = call.pagingOptions(SortProperty("username", SortDirection.ASCENDING))

            val users = userService.getUsersHavingRightForProduct(productId).mapToApi()

            call.respond(
                PagedResponse(users.sortAndPage(pagingOptions), pagingOptions.toPagingData(users.size.toLong()))
            )
        }
    }
}
