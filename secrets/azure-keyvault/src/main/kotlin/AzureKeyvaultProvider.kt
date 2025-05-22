/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.secrets.azurekeyvault

import com.azure.security.keyvault.secrets.SecretClient

import org.eclipse.apoapsis.ortserver.model.HierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.secrets.Path
import org.eclipse.apoapsis.ortserver.secrets.Secret
import org.eclipse.apoapsis.ortserver.secrets.SecretsProvider

// Regex for allowed object names in Azure Key Vault, see:
// https://learn.microsoft.com/en-us/azure/key-vault/general/about-keys-secrets-certificates#object-identifiers
// Reduce the maximum length from 127 to 100 characters to leave space for the generated prefix.
private val PATH_REGEX = Regex("^[0-9a-zA-Z\\-]{1,100}\$")

class AzureKeyvaultProvider(private val secretClient: SecretClient) : SecretsProvider {
    override fun readSecret(path: Path): Secret? =
        runCatching {
            secretClient.getSecret(path.path).value?.let { Secret(it) }
        }.getOrNull()

    override fun writeSecret(path: Path, secret: Secret) {
        secretClient.setSecret(path.path, secret.value)
    }

    override fun removeSecret(path: Path) {
        secretClient.beginDeleteSecret(path.path)
    }

    override fun createPath(id: HierarchyId, secretName: String): Path {
        check(secretName.matches(PATH_REGEX)) {
            "The secret name '$secretName' does not match the allowed pattern '$PATH_REGEX'."
        }

        val secretType = when (id) {
            is OrganizationId -> "organization-${id.value}"
            is ProductId -> "product-${id.value}"
            is RepositoryId -> "repository-${id.value}"
        }

        return Path("$secretType-$secretName")
    }
}
