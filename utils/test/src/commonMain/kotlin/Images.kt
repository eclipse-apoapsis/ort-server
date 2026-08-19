/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.utils.test

/**
 * A central place to define Docker images used by test containers throughout the code base. Keeping the images in a
 * single location makes it easier to automatically update them with Renovate.
 */
object Images {
    const val ARTEMIS = "apache/artemis:2.55.0"
    const val AZURITE = "mcr.microsoft.com/azure-storage/azurite:3.36.0"
    const val KEYCLOAK = "quay.io/keycloak/keycloak:26.6.0"
    const val LOCALSTACK = "localstack/localstack:4.14.0"
    const val POSTGRES = "postgres:15.19"
    const val RABBITMQ = "rabbitmq:4.3.4"
    const val VAULT = "hashicorp/vault:1.21.4"
}
