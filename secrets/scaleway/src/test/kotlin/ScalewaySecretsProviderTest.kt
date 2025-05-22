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

package org.eclipse.apoapsis.ortserver.secrets.scaleway

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.secrets.Secret

class ScalewaySecretsProviderTest : WordSpec({
    // Some test cases in this test spec actually connect to the real production Scaleway API. These tests are only
    // enabled if credentials are provided via environment variables.
    val config = ScalewayConfiguration(
        secretKey = System.getenv("SCW_SECRET_KEY").orEmpty(),
        projectId = System.getenv("SCW_PROJECT_ID").orEmpty()
    )

    val provider = ScalewaySecretsProvider(config)
    val path = provider.createPath(OrganizationId(1), "This_is_a_29-chr._secret_name")
    val secret = Secret("Ernie & Bert live at Sesame Street!")

    "createPath()" should {
        "create an absolute path from the path prefix and path name" {
            path.path shouldBe "/organization_1/This_is_a_29-chr._secret_name"
            path.toScaleway() shouldBe Pair("/organization_1", "This_is_a_29-chr._secret_name")
        }
    }

    // TODO: Add tests that work without accessing a real production environment.

    "A CRUD workflow" should {
        "not throw for 'removeSecret()' even if the secret does not exist".config(enabled = config.hasCredentials) {
            provider.removeSecret(path)
        }

        "return null for 'readSecret()' on a non-existing path".config(enabled = config.hasCredentials) {
            provider.readSecret(path) should beNull()
        }

        "not throw for 'writeSecret()'".config(enabled = config.hasCredentials) {
            provider.writeSecret(path, secret)
        }

        "return the secret for 'readSecret()'".config(enabled = config.hasCredentials) {
            provider.readSecret(path) shouldBe secret
        }

        "not throw for 'removeSecret()'".config(enabled = config.hasCredentials) {
            provider.removeSecret(path)
        }
    }
})
