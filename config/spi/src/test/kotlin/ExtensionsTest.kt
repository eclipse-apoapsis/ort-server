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

package org.eclipse.apoapsis.ortserver.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe

class ExtensionsTest : WordSpec({
    "resolveSecurely" should {
        "resolve a file directly inside the base directory" {
            val root = tempdir()
            val expected = root.resolve("file.txt")

            root.resolveSecurely(Path("file.txt")).canonicalFile shouldBe expected.canonicalFile
        }

        "resolve a file in a nested subdirectory" {
            val root = tempdir()
            val expected = root.resolve("sub/nested/file.txt")

            root.resolveSecurely(Path("sub/nested/file.txt")).canonicalFile shouldBe expected.canonicalFile
        }

        "resolve the base directory itself for an empty path" {
            val root = tempdir()

            root.resolveSecurely(Path("")).canonicalFile shouldBe root.canonicalFile
        }

        "reject a relative path traversing above the base directory" {
            val root = tempdir().resolve("config").apply { mkdirs() }

            shouldThrow<ConfigException> {
                root.resolveSecurely(Path("../secret.txt"))
            }
        }

        "reject a deep relative path traversal" {
            val root = tempdir().resolve("config").apply { mkdirs() }

            shouldThrow<ConfigException> {
                root.resolveSecurely(Path("../../../../etc/passwd"))
            }
        }

        "reject an absolute path outside the base directory" {
            val root = tempdir()
            val outside = tempdir().resolve("secret.txt")

            shouldThrow<ConfigException> {
                root.resolveSecurely(Path(outside.absolutePath))
            }
        }
    }
})
