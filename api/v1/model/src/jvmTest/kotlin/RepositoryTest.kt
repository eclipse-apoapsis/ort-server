/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.api.v1.model.Repository

class RepositoryTest : StringSpec({
    "isValidUrl()" should {
        "validate the repository URL" {
            val validUrls = listOf(
                "http://example.com",
                "https://example.com",
                "https://example.com/path",
                "https://example.com:8080",
                "https://example.com/path?query=string",
                "https://example.com/path#fragment",
                "https://example.com/path?query=string#fragment",
                "https://example.com/path#fragment?query=string",
                "http://127.0.0.1",
                "http://localhost"
            )

            validUrls.forAll { Repository.isValidUrl(it) shouldBe true }

            val invalidUrls = listOf(
                "https://exam ple.com/",
                "invalidurl"
            )

            invalidUrls.forAll { Repository.isValidUrl(it) shouldBe false }
        }
    }

    "hasUserInfo()" should {
        "detect user info in the URL" {
            Repository.hasUserInfo("https://user:password@example.com") shouldBe true
            Repository.hasUserInfo("https://user@example.com") shouldBe true
            Repository.hasUserInfo("https://127.0.0.1") shouldBe false
            Repository.hasUserInfo("https://user@127.0.0.1") shouldBe true
            Repository.hasUserInfo("https://example.com") shouldBe false
        }
    }
})
