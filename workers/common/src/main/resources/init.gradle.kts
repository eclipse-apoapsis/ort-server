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

apply<InternalRepositoryPlugin>()

/**
 * A Gradle plugin that applies a central mirror configuration to the repositories of the current project.
 * This is based on https://blog.gradle.org/maven-central-mirror
 */
class InternalRepositoryPlugin : Plugin<Gradle> {
    override fun apply(gradle: Gradle) {
        // Find repositories that can be mirrored by the central mirror repository.
        // Also include the mirror repository itself to make sure that correct credentials are set.
        val canBeMirrored: Spec<MavenArtifactRepository> = Spec { r ->
            r.name == ArtifactRepositoryContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME ||
                    r.url.toString() == "#{MIRROR_URL}#"
        }

        // Helper action to set the credentials for the mirror repository.
        val configureCredentials: Action<PasswordCredentials> = Action {
            username = #{MIRROR_USERNAME}#
            password = #{MIRROR_PASSWORD}#
        }

        // Action to configure a repository to use the mirror URL and credentials.
        val useMirror: Action<MavenArtifactRepository> = Action {
            setUrl("#{MIRROR_URL}#")
            val usr: String? = #{MIRROR_USERNAME}#
            val pwd: String? = #{MIRROR_PASSWORD}#
            if (usr != null && pwd != null) {
                credentials(configureCredentials)
            }
        }

        // Action to find and configure the repositories that need to be replaced with the mirror.
        val configureMirror: Action<RepositoryHandler> = Action {
            withType(MavenArtifactRepository::class.java)
                .matching(canBeMirrored)
                .configureEach(useMirror)
        }

        gradle.beforeSettings(Action {
            configureMirror.execute(buildscript.repositories)
            configureMirror.execute(pluginManagement.repositories)
        })

        // This requires Gradle 6.8 or later.
        gradle.settingsEvaluated(Action {
            configureMirror.execute(dependencyResolutionManagement.repositories)
        })

        gradle.beforeProject(Action {
            configureMirror.execute(buildscript.repositories)
        })

        gradle.afterProject(Action {
            configureMirror.execute(repositories)
        })
    }
}
