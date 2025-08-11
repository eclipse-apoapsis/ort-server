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

import java.io.File
import java.util.zip.ZipFile

import kotlin.collections.forEach
import kotlin.sequences.forEach

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar

/**
 * A Gradle plugin that collects information about available ORT plugins during build time and makes them available in
 * a module.
 */
class PluginInfoCollectorPlugin : Plugin<Project> {
    companion object {
        /** The directory (under the _build_ folder) where to collect plugin information files extracted from jars. */
        private const val COLLECT_DIR = "plugin-infos"

        /** The name of the configuration that contains the dependencies to be analyzed for plugin information. */
        internal const val DEPENDENCY_CONFIGURATION_NAME = "compileOnly"
    }

    override fun apply(project: Project) {
        val dependenciesTask =
            project.tasks.register("collectDependencyPlugins", CollectDependencyPluginsTask::class.java) {
                group = "plugin-info"
                description = "Collects plugin information from project dependencies."
                outputDirectory.set(project.layout.buildDirectory.dir(COLLECT_DIR).get())
            }

        project.afterEvaluate {
            configureCollectDependenciesTask(project, dependenciesTask.get())
        }
    }

    /**
     * Configure the given [task] with the jar files to be processed based on the dependencies of the given [project].
     * Handle both external and project dependencies, which require different handling to access their JAR files.
     */
    private fun configureCollectDependenciesTask(project: Project, task: CollectDependencyPluginsTask) {
        val jarFiles = mutableListOf<File>()

        // The compileOnly configuration cannot be resolved; so a detached configuration is created.
        project.configurations.findByName(DEPENDENCY_CONFIGURATION_NAME)?.also { configuration ->
            val detachedConfig = project.configurations.detachedConfiguration()
            configuration.dependencies.forEach { dep ->
                detachedConfig.dependencies.add(dep)

                (dep as? ProjectDependency)?.also { projectDependency ->
                    project.gradle.projectsEvaluated {
                        val jarTask = project.project(projectDependency.path)
                            .tasks.named("jar", Jar::class.java)
                        task.jarFiles.from(jarTask.get().archiveFile)
                        task.dependsOn(jarTask)
                    }
                }
            }

            detachedConfig.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
                if (artifact.file.extension == "jar") {
                    jarFiles.add(artifact.file)
                }
            }
        }

        task.jarFiles.setFrom(jarFiles)
    }
}

/**
 * A task that iterates over the dependencies of the current project and extracts information about all ORT plugins
 * that are referenced by them.
 */
abstract class CollectDependencyPluginsTask : DefaultTask() {
    companion object {
        /** Regular expression to match plugin information files in JARs. */
        private val regexPluginFile = Regex("META-INF/plugin/.*\\.json")
    }

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val jarFiles: ConfigurableFileCollection

    /** The directory where the collected plugin information files will be stored. */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun findPluginInfosInDependencies() {
        val extractDir = outputDirectory.get().asFile
        extractDir.deleteRecursively()
        extractDir.mkdirs()

        jarFiles.forEach { jarFile ->
            extractPluginInfosFromJar(jarFile, extractDir)
        }
    }

    /**
     * Read the given [jarFile] and extract all information files about ORT plugins to the given [extractDir].
     */
    private fun extractPluginInfosFromJar(jarFile: File, extractDir: File) {
        ZipFile(jarFile).use { zip ->
            zip.entries().asSequence()
                .filter { entry ->
                    !entry.isDirectory && entry.name.matches(regexPluginFile)
                }.forEach { entry ->
                    val entryFile = File(entry.name)
                    val targetFile = extractDir.resolve(entryFile.name)

                    zip.getInputStream(entry).use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
        }
    }
}
