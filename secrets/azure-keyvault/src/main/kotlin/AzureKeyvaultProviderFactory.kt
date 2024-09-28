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

import com.azure.identity.DefaultAzureCredentialBuilder
import com.azure.security.keyvault.secrets.SecretClientBuilder

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.secrets.SecretsProvider
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactory

class AzureKeyvaultProviderFactory : SecretsProviderFactory {
    companion object {
        const val KEY_VAULT_NAME_PROPERTY = "azureKeyVaultName"
    }

    override val name = "azure-keyvault"

    override fun createProvider(configManager: ConfigManager): SecretsProvider {
        val defaultCredential = DefaultAzureCredentialBuilder().build()

        val secretClient = SecretClientBuilder()
            .vaultUrl("https://${configManager.getString(KEY_VAULT_NAME_PROPERTY)}.vault.azure.net/")
            .credential(defaultCredential)
            .buildClient()

        return AzureKeyvaultProvider(secretClient)
    }
}
