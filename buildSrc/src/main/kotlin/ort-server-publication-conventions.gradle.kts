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

plugins {
    // Apply third-party plugins.
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    pom {
        name = project.name
        description = "Part of the ORT Server, the reference implementation of Eclipse Apoapsis."
        url = "https://projects.eclipse.org/projects/technology.apoapsis"

        developers {
            developer {
                name = "The ORT Server Project Authors"
                email = "apoapsis-dev@eclipse.org"
                url = "https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE"
            }
        }

        licenses {
            license {
                name = "Apache-2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0"
            }
        }

        scm {
            connection = "scm:git:https://github.com/eclipse-apoapsis/ort-server.git"
            developerConnection = "scm:git:git@github.com:eclipse-apoapsis/ort-server.git"
            tag = version.toString()
            url = "https://github.com/eclipse-apoapsis/ort-server"
        }
    }

    publishing {
        repositories {
            maven {
                name = "githubPackages"

                // The owner and repository need to be configured in the `GITHUB_REPOSITORY` environment variable,
                // for example "octocat/Hello-Word". This variable is set by default in GitHub actions, see
                // https://docs.github.com/en/actions/learn-github-actions/variables#default-environment-variables.
                url = uri("https://maven.pkg.github.com/${System.getenv("GITHUB_REPOSITORY")}")

                // Username and password (a personal GitHub access token) should be specified as
                // `githubPackagesUsername` and `githubPackagesPassword` Gradle properties or alternatively as
                // `ORG_GRADLE_PROJECT_githubPackagesUsername` and `ORG_GRADLE_PROJECT_githubPackagesPassword`
                // environment variables.
                credentials(PasswordCredentials::class)
            }
        }
    }

    publishToMavenCentral()
}
