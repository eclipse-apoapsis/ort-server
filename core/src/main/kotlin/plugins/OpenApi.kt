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

package org.eclipse.apoapsis.ortserver.core.plugins

import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.config.AuthType
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.github.smiley4.schemakenerator.core.CoreSteps.addMissingSupertypeSubtypeRelations
import io.github.smiley4.schemakenerator.core.CoreSteps.handleNameAnnotation
import io.github.smiley4.schemakenerator.reflection.ReflectionSteps.analyzeTypeUsingReflection
import io.github.smiley4.schemakenerator.reflection.ReflectionSteps.collectSubTypes
import io.github.smiley4.schemakenerator.reflection.data.TypeRedirect
import io.github.smiley4.schemakenerator.swagger.SwaggerSteps
import io.github.smiley4.schemakenerator.swagger.SwaggerSteps.compileReferencingRoot
import io.github.smiley4.schemakenerator.swagger.SwaggerSteps.generateSwaggerSchema
import io.github.smiley4.schemakenerator.swagger.SwaggerSteps.handleCoreAnnotations
import io.github.smiley4.schemakenerator.swagger.SwaggerSteps.withTitle
import io.github.smiley4.schemakenerator.swagger.data.RefType
import io.github.smiley4.schemakenerator.swagger.data.TitleType

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.api.v1.model.CredentialsType
import org.eclipse.apoapsis.ortserver.api.v1.model.OptionalValue
import org.eclipse.apoapsis.ortserver.api.v1.model.RepositoryType
import org.eclipse.apoapsis.ortserver.components.authorization.SecurityConfigurations
import org.eclipse.apoapsis.ortserver.utils.system.ORT_SERVER_VERSION

import org.koin.ktor.ext.inject

fun Application.configureOpenApi() {
    val config: ApplicationConfig by inject()

    install(OpenApi) {
        // Don't show the routes providing the custom json-schemas.
        pathFilter = { _, url -> url.firstOrNull() != "schemas" }

        security {
            defaultSecuritySchemeNames = listOf(SecurityConfigurations.TOKEN)
            defaultUnauthorizedResponse {
                description = "Invalid Token"
            }

            securityScheme(SecurityConfigurations.TOKEN) {
                type = AuthType.OAUTH2
                flows {
                    authorizationCode {
                        authorizationUrl = "${config.property("jwt.issuer").getString()}/protocol/openid-connect/auth"
                        tokenUrl = "${config.property("jwt.issuer").getString()}/protocol/openid-connect/token"
                        scopes = emptyMap()
                    }
                }
            }
        }

        info {
            title = "ORT Server API"
            version = ORT_SERVER_VERSION
            license {
                name = "Apache-2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0"
            }
        }

        server {
            val scheme = config.property("ktor.deployment.publicScheme").getString()
            val fqdn = config.property("ktor.deployment.publicFqdn").getString()
            val port = config.property("ktor.deployment.publicPort").getString()
            url = "$scheme://$fqdn:$port"
            description = "ORT server"
        }

        // OpenAPI provides tags not only on operation level, but also on root level.
        // This allows to provide additional information to the tags, and actually the order
        // of the tags on root level also defines the order of appearance of the operations
        // (belonging to these tags) in the Swagger UI.
        // See https://swagger.io/docs/specification/grouping-operations-with-tags/ for details.
        tags {
            tag("Health") { }
            tag("Organizations") { }
            tag("Products") { }
            tag("Repositories") { }
            tag("Runs") { }
            tag("Admin") { }
            tag("Versions") { }
        }

        schemas {
            generator = { type ->
                type
                    .collectSubTypes()
                    .analyzeTypeUsingReflection {
                        // Replace Instants with Strings in the generated schema to avoid breaking changes in the UI.
                        // This might later be replaced with a proper schema for dates.
                        redirect {
                            from<Instant>()
                            to<String>()
                        }

                        // Replace OptionalValue with its type argument in the generated schema as the class is only
                        // required in Kotlin code to model the difference between not present and null. Data classes
                        // using OptionalValue must provide a default value to properly mark the element as not required
                        // in the generated schema.
                        redirect {
                            from<OptionalValue<String>>(nullability = TypeRedirect.FromNullability.MATCH)
                            to<String>()
                        }
                        redirect {
                            from<OptionalValue<String?>>(nullability = TypeRedirect.FromNullability.MATCH)
                            to<String?>(nullability = TypeRedirect.ToNullability.REPLACE)
                        }
                        redirect {
                            from<OptionalValue<RepositoryType>>(nullability = TypeRedirect.FromNullability.MATCH)
                            to<RepositoryType>()
                        }
                        redirect {
                            from<OptionalValue<Set<CredentialsType>>>(nullability = TypeRedirect.FromNullability.MATCH)
                            to<Set<CredentialsType>>()
                        }
                    }
                    .addMissingSupertypeSubtypeRelations()
                    .handleNameAnnotation()
                    .generateSwaggerSchema {
                        optionals = SwaggerSteps.RequiredHandling.NON_REQUIRED
                    }
                    .handleCoreAnnotations()
                    .withTitle(TitleType.OPENAPI_SIMPLE)
                    .compileReferencingRoot(pathType = RefType.OPENAPI_SIMPLE)
            }
        }
    }

    routing {
        route("swagger-ui") {
            swaggerUI("/swagger-ui/api.json")

            route("api.json") {
                openApi()
            }
        }
    }
}
