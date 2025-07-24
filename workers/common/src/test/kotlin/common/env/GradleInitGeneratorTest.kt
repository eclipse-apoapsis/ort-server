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

package org.eclipse.apoapsis.ortserver.workers.common.env

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty

import io.mockk.every

import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.services.config.MavenCentralMirror
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.GradleDefinition

class GradleInitGeneratorTest : WordSpec({
    "environmentDefinitionType" should {
        "return the correct definition class" {
            val definitionType = GradleInitGenerator().environmentDefinitionType

            definitionType shouldBe GradleDefinition::class.java
        }
    }

    "generate" should {
        "generate the file at the correct location" {
            val mockBuilder = MockConfigFileBuilder()

            GradleInitGenerator().generate(mockBuilder.builder, emptyList())

            mockBuilder.homeFileName shouldBe ".gradle/init.gradle.kts"
        }

        "generate repositories blocks from definitions" {
            val secUser1 = MockConfigFileBuilder.createSecret("user1Secret")
            val secPass1 = MockConfigFileBuilder.createSecret("pass1Secret")
            val secUser2 = MockConfigFileBuilder.createSecret("user2Secret")
            val secPass2 = MockConfigFileBuilder.createSecret("pass2Secret")

            val definition1 = GradleDefinition(
                MockConfigFileBuilder.createInfrastructureService("https://repo1.example.org", secUser1, secPass1),
                emptySet()
            )

            val definition2 = GradleDefinition(
                MockConfigFileBuilder.createInfrastructureService("https://repo2.example.org", secUser2, secPass2),
                emptySet()
            )

            val mockBuilder = MockConfigFileBuilder()

            GradleInitGenerator().generate(mockBuilder.builder, listOf(definition1, definition2))

            val expectedContent = """
                allprojects {
                    repositories {
                        maven {
                            url = uri("https://repo1.example.org")
                            credentials {
                                username = "${MockConfigFileBuilder.testSecretRef(secUser1)}"
                                password = "${MockConfigFileBuilder.testSecretRef(secPass1)}"
                            }
                        }
                        maven {
                            url = uri("https://repo2.example.org")
                            credentials {
                                username = "${MockConfigFileBuilder.testSecretRef(secUser2)}"
                                password = "${MockConfigFileBuilder.testSecretRef(secPass2)}"
                            }
                        }
                    }

                    buildscript {
                        repositories {
                            maven {
                                url = uri("https://repo1.example.org")
                                credentials {
                                    username = "${MockConfigFileBuilder.testSecretRef(secUser1)}"
                                    password = "${MockConfigFileBuilder.testSecretRef(secPass1)}"
                                }
                            }
                            maven {
                                url = uri("https://repo2.example.org")
                                credentials {
                                    username = "${MockConfigFileBuilder.testSecretRef(secUser2)}"
                                    password = "${MockConfigFileBuilder.testSecretRef(secPass2)}"
                                }
                            }
                        }
                    }
                }

                settingsEvaluated {
                    settings.pluginManagement {
                        repositories {
                            maven {
                                url = uri("https://repo1.example.org")
                                credentials {
                                    username = "${MockConfigFileBuilder.testSecretRef(secUser1)}"
                                    password = "${MockConfigFileBuilder.testSecretRef(secPass1)}"
                                }
                            }
                            maven {
                                url = uri("https://repo2.example.org")
                                credentials {
                                    username = "${MockConfigFileBuilder.testSecretRef(secUser2)}"
                                    password = "${MockConfigFileBuilder.testSecretRef(secPass2)}"
                                }
                            }
                            gradlePluginPortal()
                        }
                    }

                    settings.dependencyResolutionManagement {
                        repositories {
                            maven {
                                url = uri("https://repo1.example.org")
                                credentials {
                                    username = "${MockConfigFileBuilder.testSecretRef(secUser1)}"
                                    password = "${MockConfigFileBuilder.testSecretRef(secPass1)}"
                                }
                            }
                            maven {
                                url = uri("https://repo2.example.org")
                                credentials {
                                    username = "${MockConfigFileBuilder.testSecretRef(secUser2)}"
                                    password = "${MockConfigFileBuilder.testSecretRef(secPass2)}"
                                }
                            }
                        }
                    }
                }

            """.trimIndent()

            val content = mockBuilder.generatedText()
            content shouldBe expectedContent
        }

        "generate repositories section for both GradleDefinition and MavenCentralMirror if URL differs" {
            val secUser = MockConfigFileBuilder.createSecret("userSecret")
            val secPass = MockConfigFileBuilder.createSecret("passSecret")

            val definition1 = GradleDefinition(
                MockConfigFileBuilder.createInfrastructureService("https://repo.example.org", secUser, secPass),
                emptySet()
            )

            val username = "test-username"
            val infraUsernameSecret = MockConfigFileBuilder.createSecret("infra-secret-username")
            val password = "test-password"
            val infraPasswordSecret = MockConfigFileBuilder.createSecret("infra-secret-password")
            val mavenCentralMirror = MavenCentralMirror(
                id = "central",
                name = "Maven Central",
                url = "https://different-repo.example.org",
                mirrorOf = "central",
                usernameSecret = infraUsernameSecret.name,
                passwordSecret = infraPasswordSecret.name
            )

            val mockBuilder = MockConfigFileBuilder()
            every { mockBuilder.infraSecretResolverFun.invoke(Path(infraUsernameSecret.path)) } returns username
            every { mockBuilder.infraSecretResolverFun.invoke(Path(infraPasswordSecret.path)) } returns password
            every { mockBuilder.adminConfig.mavenCentralMirror } returns mavenCentralMirror

            GradleInitGenerator().generate(mockBuilder.builder, listOf(definition1))

            val expectedContent = """
                allprojects {
                    repositories {
                        maven {
                            url = uri("${mavenCentralMirror.url}")
                            credentials {
                                username = "$username"
                                password = "$password"
                            }
                        }
                        maven {
                            url = uri("https://repo.example.org")
                            credentials {
                                username = "${MockConfigFileBuilder.testSecretRef(secUser)}"
                                password = "${MockConfigFileBuilder.testSecretRef(secPass)}"
                            }
                        }
                    }

                    buildscript {
                        repositories {
                            maven {
                                url = uri("${mavenCentralMirror.url}")
                                credentials {
                                    username = "$username"
                                    password = "$password"
                                }
                            }
                            maven {
                                url = uri("https://repo.example.org")
                                credentials {
                                    username = "${MockConfigFileBuilder.testSecretRef(secUser)}"
                                    password = "${MockConfigFileBuilder.testSecretRef(secPass)}"
                                }
                            }
                        }
                    }
                }

                settingsEvaluated {
                    settings.pluginManagement {
                        repositories {
                            maven {
                                url = uri("${mavenCentralMirror.url}")
                                credentials {
                                    username = "$username"
                                    password = "$password"
                                }
                            }
                            maven {
                                url = uri("https://repo.example.org")
                                credentials {
                                    username = "${MockConfigFileBuilder.testSecretRef(secUser)}"
                                    password = "${MockConfigFileBuilder.testSecretRef(secPass)}"
                                }
                            }
                            gradlePluginPortal()
                        }
                    }

                    settings.dependencyResolutionManagement {
                        repositories {
                            maven {
                                url = uri("${mavenCentralMirror.url}")
                                credentials {
                                    username = "$username"
                                    password = "$password"
                                }
                            }
                            maven {
                                url = uri("https://repo.example.org")
                                credentials {
                                    username = "${MockConfigFileBuilder.testSecretRef(secUser)}"
                                    password = "${MockConfigFileBuilder.testSecretRef(secPass)}"
                                }
                            }
                        }
                    }
                }

            """.trimIndent()

            val content = mockBuilder.generatedText()
            content shouldBe expectedContent
        }

        "prefer GradleDefinition over MavenCentralMirror if URLs match" {
            val repositoryUrl = "https://repo.example.org"
            val secUser = MockConfigFileBuilder.createSecret("userSecret")
            val secPass = MockConfigFileBuilder.createSecret("passSecret")

            val definition1 = GradleDefinition(
                MockConfigFileBuilder.createInfrastructureService(repositoryUrl, secUser, secPass),
                emptySet()
            )

            val username = "test-username"
            val infraUsernameSecret = MockConfigFileBuilder.createSecret("infra-secret-username")
            val password = "test-password"
            val infraPasswordSecret = MockConfigFileBuilder.createSecret("infra-secret-password")
            val mavenCentralMirror = MavenCentralMirror(
                id = "central",
                name = "Maven Central",
                url = repositoryUrl,
                mirrorOf = "central",
                usernameSecret = infraUsernameSecret.name,
                passwordSecret = infraPasswordSecret.name
            )

            val mockBuilder = MockConfigFileBuilder()
            every { mockBuilder.infraSecretResolverFun.invoke(Path(infraUsernameSecret.path)) } returns username
            every { mockBuilder.infraSecretResolverFun.invoke(Path(infraPasswordSecret.path)) } returns password
            every { mockBuilder.adminConfig.mavenCentralMirror } returns mavenCentralMirror

            GradleInitGenerator().generate(mockBuilder.builder, listOf(definition1))

            val expectedContent = """
                allprojects {
                    repositories {
                        maven {
                            url = uri("$repositoryUrl")
                            credentials {
                                username = "${MockConfigFileBuilder.testSecretRef(secUser)}"
                                password = "${MockConfigFileBuilder.testSecretRef(secPass)}"
                            }
                        }
                    }

                    buildscript {
                        repositories {
                            maven {
                                url = uri("$repositoryUrl")
                                credentials {
                                    username = "${MockConfigFileBuilder.testSecretRef(secUser)}"
                                    password = "${MockConfigFileBuilder.testSecretRef(secPass)}"
                                }
                            }
                        }
                    }
                }

                settingsEvaluated {
                    settings.pluginManagement {
                        repositories {
                            maven {
                                url = uri("$repositoryUrl")
                                credentials {
                                    username = "${MockConfigFileBuilder.testSecretRef(secUser)}"
                                    password = "${MockConfigFileBuilder.testSecretRef(secPass)}"
                                }
                            }
                            gradlePluginPortal()
                        }
                    }

                    settings.dependencyResolutionManagement {
                        repositories {
                            maven {
                                url = uri("$repositoryUrl")
                                credentials {
                                    username = "${MockConfigFileBuilder.testSecretRef(secUser)}"
                                    password = "${MockConfigFileBuilder.testSecretRef(secPass)}"
                                }
                            }
                        }
                    }
                }

            """.trimIndent()

            val content = mockBuilder.generatedText()
            content shouldBe expectedContent
        }

        "generate an empty file if MavenCentralMirror is null" {
            val mockBuilder = MockConfigFileBuilder()

            GradleInitGenerator().generate(mockBuilder.builder, emptyList())

            mockBuilder.generatedText().shouldBeEmpty()
        }

        "generate repositories blocks without credentials if MavenCentralMirror has no credentials" {
            val mavenCentralMirror = MavenCentralMirror(
                id = "central",
                name = "Maven Central",
                url = "https://repo.maven.apache.org/maven2",
                mirrorOf = "central"
            )

            val mockBuilder = MockConfigFileBuilder()
            every { mockBuilder.adminConfig.mavenCentralMirror } returns mavenCentralMirror

            GradleInitGenerator().generate(mockBuilder.builder, emptyList())

            val expectedLines = """
                allprojects {
                    repositories {
                        maven {
                            url = uri("${mavenCentralMirror.url}")
                        }
                    }

                    buildscript {
                        repositories {
                            maven {
                                url = uri("${mavenCentralMirror.url}")
                            }
                        }
                    }
                }

                settingsEvaluated {
                    settings.pluginManagement {
                        repositories {
                            maven {
                                url = uri("${mavenCentralMirror.url}")
                            }
                            gradlePluginPortal()
                        }
                    }

                    settings.dependencyResolutionManagement {
                        repositories {
                            maven {
                                url = uri("${mavenCentralMirror.url}")
                            }
                        }
                    }
                }
            """.trimIndent().lines()

            val lines = mockBuilder.generatedLines()
            lines shouldContainExactly expectedLines
        }

        "generate repositories blocks with credentials if MavenCentralMirror has credentials" {
            val username = "test-username"
            val infraUsernameSecret = MockConfigFileBuilder.createSecret("infra-secret-username")
            val password = "test-password"
            val infraPasswordSecret = MockConfigFileBuilder.createSecret("infra-secret-password")
            val mavenCentralMirror = MavenCentralMirror(
                id = "central",
                name = "Maven Central",
                url = "https://repo.maven.apache.org/maven2",
                mirrorOf = "central",
                usernameSecret = infraUsernameSecret.name,
                passwordSecret = infraPasswordSecret.name
            )

            val mockBuilder = MockConfigFileBuilder()
            every { mockBuilder.infraSecretResolverFun.invoke(Path(infraUsernameSecret.path)) } returns username
            every { mockBuilder.infraSecretResolverFun.invoke(Path(infraPasswordSecret.path)) } returns password
            every { mockBuilder.adminConfig.mavenCentralMirror } returns mavenCentralMirror

            GradleInitGenerator().generate(mockBuilder.builder, emptyList())

            val expectedLines = """
                allprojects {
                    repositories {
                        maven {
                            url = uri("${mavenCentralMirror.url}")
                            credentials {
                                username = "$username"
                                password = "$password"
                            }
                        }
                    }

                    buildscript {
                        repositories {
                            maven {
                                url = uri("${mavenCentralMirror.url}")
                                credentials {
                                    username = "$username"
                                    password = "$password"
                                }
                            }
                        }
                    }
                }

                settingsEvaluated {
                    settings.pluginManagement {
                        repositories {
                            maven {
                                url = uri("${mavenCentralMirror.url}")
                                credentials {
                                    username = "$username"
                                    password = "$password"
                                }
                            }
                            gradlePluginPortal()
                        }
                    }

                    settings.dependencyResolutionManagement {
                        repositories {
                            maven {
                                url = uri("${mavenCentralMirror.url}")
                                credentials {
                                    username = "$username"
                                    password = "$password"
                                }
                            }
                        }
                    }
                }
            """.trimIndent().lines()
            val lines = mockBuilder.generatedLines()
            lines shouldContainExactly expectedLines
        }
    }
})
