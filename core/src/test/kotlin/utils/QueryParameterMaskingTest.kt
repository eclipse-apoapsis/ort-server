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
package org.eclipse.apoapsis.ortserver.core.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

class QueryParameterMaskingTest : WordSpec({
    "maskQueryString()" should {
        "return the path if there is no query string" {
            maskQueryString("/api/v1/runs", "") shouldBe "/api/v1/runs"
        }

        "keep the values of logged parameters in their original encoding" {
            maskQueryString("/api/v1/runs", "limit=20&sort=-createdAt&purl=pkg%3Amaven%2Fa%2Fb%401.0") shouldBe
                "/api/v1/runs?limit=20&sort=-createdAt&purl=pkg%3Amaven%2Fa%2Fb%401.0"
        }

        "mask the values of masked parameters" {
            maskQueryString("/api/v1/admin/users", "username=jdoe&filter=some%20text") shouldBe
                "/api/v1/admin/users?username=***&filter=***"
        }

        "mask the values of unknown parameters" {
            maskQueryString("/api/v1/runs", "limit=20&unknown=value") shouldBe "/api/v1/runs?limit=20&unknown=***"
        }

        "mask each value of a repeated parameter" {
            maskQueryString("/api/v1/runs", "search=a&search=b&limit=1") shouldBe
                "/api/v1/runs?search=***&search=***&limit=1"
        }

        "decode parameter names to check whether they are logged" {
            maskQueryString("/api/v1/runs", "%6Cimit=20") shouldBe "/api/v1/runs?%6Cimit=20"
        }

        "keep parameters without a value" {
            maskQueryString("/api/v1/runs", "resolved&username") shouldBe "/api/v1/runs?resolved&username"
        }

        "mask parameters with names that cannot be decoded" {
            maskQueryString("/api/v1/runs", "%zz=value") shouldBe "/api/v1/runs?%zz=***"
        }
    }
})
