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

package org.ossreviewtoolkit.server.core.apiDocs

import io.github.smiley4.ktorswaggerui.dsl.OpenApiRoute

import io.ktor.http.HttpStatusCode

import org.ossreviewtoolkit.server.api.v1.CreateOrganization
import org.ossreviewtoolkit.server.api.v1.CreateProduct
import org.ossreviewtoolkit.server.api.v1.Organization
import org.ossreviewtoolkit.server.api.v1.Product
import org.ossreviewtoolkit.server.api.v1.UpdateOrganization

val getOrganizationById: OpenApiRoute.() -> Unit = {
    operationId = "GetOrganizationById"
    summary = "Get details of an organization."
    tags = listOf("Organizations")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<Organization> {
                example("Get Organization", Organization(1, "My Organization", "This is my organization."))
            }
        }
    }
}

val getOrganizations: OpenApiRoute.() -> Unit = {
    operationId = "GetOrganizations"
    summary = "List all organizations."
    tags = listOf("Organizations")

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<List<Organization>> {
                example(
                    "List all organizations",
                    listOf(
                        Organization(1, "First Organization", "Description"),
                        Organization(2, "Second Organization")
                    )
                )
            }
        }
    }
}

val postOrganizations: OpenApiRoute.() -> Unit = {
    operationId = "PostOrganizations"
    summary = "Create an organization."
    tags = listOf("Organizations")

    request {
        jsonBody<CreateOrganization> {
            example("Create Organization", CreateOrganization("My Organization", "This is my organization."))
        }
    }

    response {
        HttpStatusCode.Created to {
            description = "Success"
            jsonBody<Organization> {
                example("Create Organization", Organization(1, "My Organization", "This is my organization."))
            }
        }
    }
}

val patchOrganizationById: OpenApiRoute.() -> Unit = {
    operationId = "PatchOrganizationById"
    summary = "Update an organization."
    tags = listOf("Organizations")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
        }
        jsonBody<UpdateOrganization> {
            description = "Set the values that should be updated. To delete a value, set it explicitly to null."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<Organization> {
                example("Update Organization", Organization(1, "My updated Organization", "Updated description."))
            }
        }
    }
}

val deleteOrganizationById: OpenApiRoute.() -> Unit = {
    operationId = "DeleteOrganizationById"
    summary = "Delete an organization."
    tags = listOf("Organizations")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
        }
    }

    response {
        HttpStatusCode.NoContent to {
            description = "Success"
        }
    }
}

val getOrganizationProducts: OpenApiRoute.() -> Unit = {
    operationId = "GetOrganizationProducts"
    summary = "Get all products of an organization."
    tags = listOf("Products")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<List<Product>> {
                example(
                    "Get products of an organization",
                    listOf(
                        Product(1, "My first product", "Description"),
                        Product(2, "My second product")
                    )
                )
            }
        }
    }
}

val postProduct: OpenApiRoute.() -> Unit = {
    operationId = "PostProduct"
    summary = "Create a product for the organization."
    tags = listOf("Products")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
        }
        jsonBody<CreateProduct> {
            example("Create product", CreateProduct("My product", "Description"))
        }
    }

    response {
        HttpStatusCode.Created to {
            description = "Success"
            jsonBody<Product> {
                example("Create product", Product(1, "My product", "Description"))
            }
        }
    }
}
