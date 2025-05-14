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
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateOrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateRepository
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateSecret
import org.eclipse.apoapsis.ortserver.api.v1.model.Jobs
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatistics
import org.eclipse.apoapsis.ortserver.api.v1.model.PagedResponse
import org.eclipse.apoapsis.ortserver.api.v1.model.SortDirection
import org.eclipse.apoapsis.ortserver.api.v1.model.SortProperty
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateProduct
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateSecret
import org.eclipse.apoapsis.ortserver.api.v1.model.Username
import org.eclipse.apoapsis.ortserver.components.authorization.OrtPrincipal
import org.eclipse.apoapsis.ortserver.components.authorization.getFullName
import org.eclipse.apoapsis.ortserver.components.authorization.getUserId
import org.eclipse.apoapsis.ortserver.components.authorization.getUsername
import org.eclipse.apoapsis.ortserver.components.authorization.permissions.ProductPermission
import org.eclipse.apoapsis.ortserver.components.authorization.requirePermission
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginService
import org.eclipse.apoapsis.ortserver.core.api.UserWithGroupsHelper.mapToApi
import org.eclipse.apoapsis.ortserver.core.api.UserWithGroupsHelper.sortAndPage
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteProductById
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteSecretByProductIdAndName
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteUserFromProductGroup
import org.eclipse.apoapsis.ortserver.core.apiDocs.getOrtRunStatisticsByProductId
import org.eclipse.apoapsis.ortserver.core.apiDocs.getProductById
import org.eclipse.apoapsis.ortserver.core.apiDocs.getRepositoriesByProductId
import org.eclipse.apoapsis.ortserver.core.apiDocs.getSecretByProductIdAndName
import org.eclipse.apoapsis.ortserver.core.apiDocs.getSecretsByProductId
import org.eclipse.apoapsis.ortserver.core.apiDocs.getUsersForProduct
import org.eclipse.apoapsis.ortserver.core.apiDocs.getVulnerabilitiesAcrossRepositoriesByProductId
import org.eclipse.apoapsis.ortserver.core.apiDocs.patchProductById
import org.eclipse.apoapsis.ortserver.core.apiDocs.patchSecretByProductIdAndName
import org.eclipse.apoapsis.ortserver.core.apiDocs.postOrtRunsForProduct
import org.eclipse.apoapsis.ortserver.core.apiDocs.postRepository
import org.eclipse.apoapsis.ortserver.core.apiDocs.postSecretForProduct
import org.eclipse.apoapsis.ortserver.core.apiDocs.putUserToProductGroup
import org.eclipse.apoapsis.ortserver.core.services.OrchestratorService
import org.eclipse.apoapsis.ortserver.core.utils.getDisabledPlugins
import org.eclipse.apoapsis.ortserver.core.utils.pagingOptions
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.UserDisplayName
import org.eclipse.apoapsis.ortserver.model.VulnerabilityWithAccumulatedData
import org.eclipse.apoapsis.ortserver.services.IssueService
import org.eclipse.apoapsis.ortserver.services.PackageService
import org.eclipse.apoapsis.ortserver.services.ProductService
import org.eclipse.apoapsis.ortserver.services.RepositoryService
import org.eclipse.apoapsis.ortserver.services.RuleViolationService
import org.eclipse.apoapsis.ortserver.services.SecretService
import org.eclipse.apoapsis.ortserver.services.UserService
import org.eclipse.apoapsis.ortserver.services.VulnerabilityService
import org.eclipse.apoapsis.ortserver.shared.apimodel.ErrorResponse
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireParameter

import org.koin.ktor.ext.inject

@Suppress("LongMethod")
fun Route.products() = route("products/{productId}") {
    val productService by inject<ProductService>()
    val pluginService by inject<PluginService>()
    val repositoryService by inject<RepositoryService>()
    val secretService by inject<SecretService>()
    val vulnerabilityService by inject<VulnerabilityService>()
    val issueService by inject<IssueService>()
    val ruleViolationService by inject<RuleViolationService>()
    val packageService by inject<PackageService>()
    val userService by inject<UserService>()
    val orchestratorService by inject<OrchestratorService>()

    get(getProductById) {
        requirePermission(ProductPermission.READ)

        val id = call.requireIdParameter("productId")

        val product = productService.getProduct(id)

        if (product != null) {
            call.respond(HttpStatusCode.OK, product.mapToApi())
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    patch(patchProductById) {
        requirePermission(ProductPermission.WRITE)

        val id = call.requireIdParameter("productId")
        val updateProduct = call.receive<UpdateProduct>()

        val updatedProduct =
            productService.updateProduct(id, updateProduct.name.mapToModel(), updateProduct.description.mapToModel())

        call.respond(HttpStatusCode.OK, updatedProduct.mapToApi())
    }

    delete(deleteProductById) {
        requirePermission(ProductPermission.DELETE)

        val id = call.requireIdParameter("productId")

        productService.deleteProduct(id)

        call.respond(HttpStatusCode.NoContent)
    }

    route("repositories") {
        get(getRepositoriesByProductId) {
            requirePermission(ProductPermission.READ_REPOSITORIES)

            val productId = call.requireIdParameter("productId")
            val pagingOptions = call.pagingOptions(SortProperty("url", SortDirection.ASCENDING))

            val repositoriesForProduct =
                productService.listRepositoriesForProduct(productId, pagingOptions.mapToModel())

            val pagedResponse = repositoriesForProduct.mapToApi(Repository::mapToApi)

            call.respond(HttpStatusCode.OK, pagedResponse)
        }

        post(postRepository) {
            requirePermission(ProductPermission.CREATE_REPOSITORY)

            val id = call.requireIdParameter("productId")
            val createRepository = call.receive<CreateRepository>()
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

    route("secrets") {
        get(getSecretsByProductId) {
            requirePermission(ProductPermission.READ)

            val productId = call.requireIdParameter("productId")
            val pagingOptions = call.pagingOptions(SortProperty("name", SortDirection.ASCENDING))

            val secretsForProduct = secretService.listForProduct(productId, pagingOptions.mapToModel())

            val pagedResponse = secretsForProduct.mapToApi(Secret::mapToApi)

            call.respond(HttpStatusCode.OK, pagedResponse)
        }

        route("{secretName}") {
            get(getSecretByProductIdAndName) {
                requirePermission(ProductPermission.READ)

                val productId = call.requireIdParameter("productId")
                val secretName = call.requireParameter("secretName")

                secretService.getSecretByProductIdAndName(productId, secretName)
                    ?.let { call.respond(HttpStatusCode.OK, it.mapToApi()) }
                    ?: call.respond(HttpStatusCode.NotFound)
            }

            patch(patchSecretByProductIdAndName) {
                requirePermission(ProductPermission.WRITE_SECRETS)

                val productId = call.requireIdParameter("productId")
                val secretName = call.requireParameter("secretName")
                val updateSecret = call.receive<UpdateSecret>()

                call.respond(
                    HttpStatusCode.OK,
                    secretService.updateSecretByProductAndName(
                        productId,
                        secretName,
                        updateSecret.value.mapToModel(),
                        updateSecret.description.mapToModel()
                    ).mapToApi()
                )
            }

            delete(deleteSecretByProductIdAndName) {
                requirePermission(ProductPermission.WRITE_SECRETS)

                val productId = call.requireIdParameter("productId")
                val secretName = call.requireParameter("secretName")

                secretService.deleteSecretByProductAndName(productId, secretName)

                call.respond(HttpStatusCode.NoContent)
            }
        }

        post(postSecretForProduct) {
            requirePermission(ProductPermission.WRITE_SECRETS)

            val productId = call.requireIdParameter("productId")
            val createSecret = call.receive<CreateSecret>()

            call.respond(
                HttpStatusCode.Created,
                secretService.createSecret(
                    createSecret.name,
                    createSecret.value,
                    createSecret.description,
                    null,
                    productId,
                    null
                ).mapToApi()
            )
        }
    }

    route("groups") {
        // Instead of identifying arbitrary groups with a groupId, there are only 3 groups with fixed
        // groupId "readers", "writers" or "admins".
        route("{groupId}") {
            put(putUserToProductGroup) {
                requirePermission(ProductPermission.MANAGE_GROUPS)

                val user = call.receive<Username>()
                val productId = call.requireIdParameter("productId")
                val groupId = call.requireParameter("groupId")

                productService.addUserToGroup(user.username, productId, groupId)
                call.respond(HttpStatusCode.NoContent)
            }

            delete(deleteUserFromProductGroup) {
                requirePermission(ProductPermission.MANAGE_GROUPS)

                val user = call.receive<Username>()
                val productId = call.requireIdParameter("productId")
                val groupId = call.requireParameter("groupId")

                productService.removeUserFromGroup(user.username, productId, groupId)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }

    route("vulnerabilities") {
        get(getVulnerabilitiesAcrossRepositoriesByProductId) {
            requirePermission(ProductPermission.READ)

            val productId = call.requireIdParameter("productId")
            val pagingOptions = call.pagingOptions(SortProperty("rating", SortDirection.DESCENDING))

            val repositoryIds = productService.getRepositoryIdsForProduct(productId)

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
            get(getOrtRunStatisticsByProductId) {
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
        post(postOrtRunsForProduct) {
            requirePermission(ProductPermission.TRIGGER_ORT_RUN)

            val productId = call.requireIdParameter("productId")
            val createOrtRun = call.receive<CreateOrtRun>()
            val userDisplayName = call.principal<OrtPrincipal>()?.let { principal ->
                UserDisplayName(principal.getUserId(), principal.getUsername(), principal.getFullName())
            }

            // Check if disabled plugins are used.
            val disabledPlugins = createOrtRun.getDisabledPlugins(pluginService)
            if (disabledPlugins.isNotEmpty()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(
                        message = "Disabled plugins are used.",
                        cause = "The following plugins are disabled in this ORT Server instance: " +
                                disabledPlugins.joinToString { (type, id) -> "$id ($type)" }
                    )
                )
                return@post
            }

            val repositoryIds = if (createOrtRun.repositoryIds.isEmpty()) {
                productService.getRepositoryIdsForProduct(productId)
            } else {
                val productRepoIds = productService.getRepositoryIdsForProduct(productId)
                require(createOrtRun.repositoryIds.all { it in productRepoIds }) {
                    """
                    The following repository IDs do not belong to product $productId: 
                    ${createOrtRun.repositoryIds.filter { it !in productRepoIds }}
                    """.trimIndent()
                }
                createOrtRun.repositoryIds
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
        get(getUsersForProduct) {
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
