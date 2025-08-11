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

plugins {
    // Apply precompiled plugins.
    id("ort-server-kotlin-jvm-conventions")
    id("ort-server-publication-conventions")
    id("plugin-info-collector")
}

group = "org.eclipse.apoapsis.ortserver.shared"

// This repository is needed to resolve the Gradle tooling API required by the Analyzer.
repositories {
    exclusiveContent {
        forRepository {
            maven("https://repo.gradle.org/gradle/libs-releases/")
        }

        filter {
            includeGroup("org.gradle")
        }
    }
}

dependencies {
    // Depend on all ORT projects that provide plugins, but only at compile time, so that none of these classes
    // or their dependencies are included in the final JAR.
    compileOnly(platform(ortLibs.ortPlugins.advisors))
    compileOnly(platform(ortLibs.ortPlugins.packageConfigurationProviders))
    compileOnly(platform(ortLibs.ortPlugins.packageCurationProviders))
    compileOnly(platform(ortLibs.ortPlugins.packageManagers))
    compileOnly(platform(ortLibs.ortPlugins.reporters))
    compileOnly(platform(ortLibs.ortPlugins.scanners))

    // Depend on the projects that contain custom plugins to make sure they are built before this project.
    compileOnly(projects.shared.packageCurationProviders)
    compileOnly(projects.shared.reporters)

    testImplementation(libs.kotestAssertionsCore)
    testImplementation(libs.kotestRunnerJunit5)
}

val pluginSummary = tasks.register("pluginSummary", PluginSummaryTask::class.java) {
    infoDirectories = files(project.file("build/plugin-infos"))
    outputDirectory.set(project.file("build/generated/plugin-infos"))
    dependsOn("collectDependencyPlugins")
}

val copyPluginInfos = tasks.register("copyPluginInfos", Copy::class.java) {
    group = "plugin-info"
    description = "Copies collected plugin information into the generated source set."

    from(project.file("build/plugin-infos"))
    into("build/generated/plugin-infos")

    dependsOn(pluginSummary)
}

sourceSets {
    main {
        resources {
            srcDir(copyPluginInfos)
        }
    }
}
