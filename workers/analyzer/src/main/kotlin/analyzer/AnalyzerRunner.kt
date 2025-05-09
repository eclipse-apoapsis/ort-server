/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.workers.analyzer

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import java.io.File
import java.io.IOException

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import org.eclipse.apoapsis.ortserver.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.utils.config.getInterpolatedStringOrDefault
import org.eclipse.apoapsis.ortserver.utils.config.getStringOrDefault
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerOrtConfig
import org.eclipse.apoapsis.ortserver.workers.common.env.EnvironmentForkHelper
import org.eclipse.apoapsis.ortserver.workers.common.env.config.ResolvedEnvironmentConfig
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.SecretVariableDefinition
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.SimpleVariableDefinition
import org.eclipse.apoapsis.ortserver.workers.common.mapToOrt

import org.ossreviewtoolkit.analyzer.Analyzer
import org.ossreviewtoolkit.analyzer.determineEnabledPackageManagers
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.ResolvedPackageCurations
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.readValueOrNull
import org.ossreviewtoolkit.model.writeValue
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProviderFactory
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.SimplePackageCurationProvider
import org.ossreviewtoolkit.utils.ort.ORT_REPO_CONFIG_FILENAME

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AnalyzerRunner::class.java)

class AnalyzerRunner(
    /**
     * The object for accessing configuration information. This is mainly used to create a forked JVM process with a
     * customized environment.
     */
    private val config: Config
) {
    companion object {
        /** The name of the file from which the Analyzer job configuration is read. */
        private const val ANALYZER_CONFIG_FILE = "analyzer-config.json"

        /** The name of the file to which the Analyzer result is written. */
        private const val ANALYZER_RESULT_FILE = "analyzer-result.yml"

        /** The name of the file to which an error is written in case the run fails. */
        private const val ANALYZER_ERROR_FILE = "analyzer-error.txt"

        /**
         * The name of the property for the commands to launch a new JVM process. The property defines both the
         * command and the arguments to launch a new JVM process. To split between the single parts, the separator
         * defined by the [FORK_COMMAND_SEPARATOR] property is used. Each part can contain placeholders that are
         * replaced by the actual values at runtime.
         */
        private const val FORK_COMMANDS_PROPERTY = "analyzer.forkCommands"

        /**
         * The name of the property defining the separator for splitting the fork commands into multiple parts. This
         * is needed since the full command line is defined via a single property or environment variable, but to
         * actually execute it, it needs to be split into the command and its arguments.
         */
        private const val FORK_COMMAND_SEPARATOR = "analyzer.forkCommandSeparator"

        /** The placeholder for the classpath in the arguments to launch a new JVM process. */
        private const val CLASSPATH_PLACEHOLDER = "CLASSPATH"

        /** The placeholder for the main class and its arguments in the arguments to launch a new JVM process. */
        private const val LAUNCH_PLACEHOLDER = "LAUNCH"

        /** The default separator for splitting the fork commands into multiple parts. */
        private const val DEFAULT_FORK_COMMAND_SEPARATOR = "|"

        /** The default commands to launch a new JVM process. */
        private const val DEFAULT_FORK_COMMANDS = "/bin/sh$DEFAULT_FORK_COMMAND_SEPARATOR-c" +
                "${DEFAULT_FORK_COMMAND_SEPARATOR}exec java -cp \${CLASSPATH} \${LAUNCH}"

        /**
         * An alternative function for calling the [AnalyzerRunner]. This function is used when the JVM needs to be
         * forked to make newly set environment variables effective. In this case, parameters are passed via command
         * line arguments. The first argument is a temporary directory to be used for exchanging data between the parent
         * and the forked process. Here, the serialized [AnalyzerJobConfiguration] is expected, and the resulting
         * [OrtResult] will be stored in this directory, too. The second argument is the path to the project to be
         * analyzed.
         */
        @JvmStatic
        fun main(args: Array<String>) {
            logger.info("Executing forked AnalyzerRunner with arguments: ${args.joinToString()}")

            val exchangeDir = File(args[0])

            runCatching {
                val workerOrtConfig = WorkerOrtConfig.create()
                workerOrtConfig.setUpOrtEnvironment()
                EnvironmentForkHelper.setupFork(System.`in`)

                val projectDir = File(args[1])
                val configFile = exchangeDir.resolve(ANALYZER_CONFIG_FILE)
                val resultFile = exchangeDir.resolve(ANALYZER_RESULT_FILE)

                val config = configFile.readValue<AnalyzerJobConfiguration>()
                val runner = AnalyzerRunner(ConfigFactory.empty())
                val result = runner.runInProcess(projectDir, config)

                resultFile.writeValue(result)
            }.onFailure { exception ->
                logger.error("Analyzer run failed.", exception)
                exchangeDir.resolve(ANALYZER_ERROR_FILE).writeText(exception.toString())
            }
        }
    }

    /**
     * Run the analyzer for the given [inputDir] using the provided [config] and return the resulting [OrtResult].
     * Depending on the [environmentConfig], the analyzer is run in the same JVM or in a forked JVM with a customized
     * environment.
     */
    suspend fun run(
        context: WorkerContext,
        inputDir: File,
        config: AnalyzerJobConfiguration,
        environmentConfig: ResolvedEnvironmentConfig
    ): OrtResult {
        val packageCurationProviderConfigs = context.resolveProviderPluginConfigSecrets(config.packageCurationProviders)
        val resolvedConfig = config.copy(packageCurationProviders = packageCurationProviderConfigs)

        return if (environmentConfig.environmentVariables.isEmpty()) {
            runInProcess(inputDir, resolvedConfig)
        } else {
            runForked(context, inputDir, resolvedConfig, environmentConfig)
        }
    }

    /**
     * Create a [ProcessBuilder] for running the [AnalyzerRunner] in a forked JVM with a customized environment.
     * Use the given [context] to resolve secrets from the [environmentConfig] and set them as environment variables.
     * Generate the command line arguments for the forked process based on the configuration of the Analyzer worker
     * and the given [exchangeDir] and [inputDir]: The command and the arguments to launch a new JVM process can be
     * defined in the following configuration properties:
     * - `analyzer.forkCommand`: The command to launch a new JVM process.
     * - `analyzer.forkArgs`: The arguments to launch a new JVM process. The value of this property can contain
     *    placeholders that are replaced by the actual values at runtime. The following placeholders are supported:
     *    - `${CLASSPATH}`: Will be replaced by the classpath of the current JVM.
     *    - `${LAUNCH}`: Will be replaced by the fully qualified name of the main class to be executed and the
     *      arguments to be passed to it.
     */
    internal suspend fun createProcessBuilder(
        context: WorkerContext,
        exchangeDir: File,
        inputDir: File,
        environmentConfig: ResolvedEnvironmentConfig
    ): ProcessBuilder {
        val placeholders = mapOf(
            CLASSPATH_PLACEHOLDER to System.getProperty("java.class.path"),
            LAUNCH_PLACEHOLDER to "${AnalyzerRunner::class.qualifiedName} " +
                    "${exchangeDir.absolutePath} ${inputDir.absolutePath}"
        )
        val cmd = config.getInterpolatedStringOrDefault(FORK_COMMANDS_PROPERTY, DEFAULT_FORK_COMMANDS, placeholders)
        val commands = cmd.split(config.getStringOrDefault(FORK_COMMAND_SEPARATOR, DEFAULT_FORK_COMMAND_SEPARATOR))
        val processBuilder = ProcessBuilder(commands)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)

        val allSecrets = environmentConfig.environmentVariables
            .filterIsInstance<SecretVariableDefinition>()
            .map { it.valueSecret }
        val resolvedSecrets = context.resolveSecrets(*allSecrets.toTypedArray())
        val environment = processBuilder.environment()
        environmentConfig.environmentVariables.forEach { variable ->
            when (variable) {
                is SecretVariableDefinition -> environment[variable.name] = resolvedSecrets[variable.valueSecret]
                is SimpleVariableDefinition -> environment[variable.name] = variable.value
            }
        }

        return processBuilder
    }

    /**
     * Run the Analyzer in a forked JVM with a customized [environmentConfig] using the given [context],
     * [project directory][inputDir], and [config]. This function is used if custom environment variables need to be
     * set.
     */
    internal suspend fun runForked(
        context: WorkerContext,
        inputDir: File,
        config: AnalyzerJobConfiguration,
        environmentConfig: ResolvedEnvironmentConfig
    ): OrtResult {
        val exchangeDir = context.createTempDir()
        exchangeDir.resolve(ANALYZER_CONFIG_FILE).writeValue(config)

        val processBuilder = createProcessBuilder(context, exchangeDir, inputDir, environmentConfig)

        logger.info("Starting forked AnalyzerRunner with command: ${processBuilder.command()}")
        withContext(Dispatchers.IO) {
            val process = processBuilder.start()
            process.outputStream.use { pipe ->
                EnvironmentForkHelper.prepareFork(pipe)
            }

            val exitCode = process.waitFor()

            logger.info("Forked AnalyzerRunner process finished with exit code $exitCode.")
        }

        return exchangeDir.resolve(ANALYZER_RESULT_FILE).takeIf { it.isFile }?.readValue()
            ?: handleForkError(exchangeDir)
    }

    /**
     * Handle the case that the forked process did not produce a result file. Try to generate an error message from the
     * error file or use a standard message if this file does not exist either. Throw an [IOException] with this
     * message.
     */
    private fun handleForkError(exchangeDir: File): Nothing {
        val errorFile = exchangeDir.resolve(ANALYZER_ERROR_FILE)
        val errorMessage = errorFile.takeIf { it.isFile }?.readText()
            ?: "The forked process died without writing an error file."
        throw IOException(errorMessage)
    }

    /**
     * Run the Analyzer for the given [inputDir] using the provided [config] and return the resulting [OrtResult].
     * This function is used if no custom environment variables need to be set, and therefore, the Analyzer can be
     * invoked directly.
     */
    internal fun runInProcess(inputDir: File, config: AnalyzerJobConfiguration): OrtResult {
        val ortPackageManagerOptions =
            config.packageManagerOptions?.map { entry -> entry.key to entry.value.mapToOrt() }?.toMap()

        val analyzerConfigFromJob = AnalyzerConfiguration(
            config.allowDynamicVersions,
            config.enabledPackageManagers ?: AnalyzerConfiguration().enabledPackageManagers,
            config.disabledPackageManagers,
            ortPackageManagerOptions,
            config.skipExcluded ?: false
        )

        val repositoryConfigPath = config.repositoryConfigPath ?: ORT_REPO_CONFIG_FILENAME
        val repositoryConfigFile = inputDir.resolve(repositoryConfigPath)

        require(repositoryConfigFile.canonicalFile.startsWith(inputDir)) {
            "The `repositoryConfigPath` with value '$repositoryConfigPath' resolves to the file " +
                    "'${repositoryConfigFile.absolutePath}' which is not in the input directory " +
                    "'${inputDir.absolutePath}'."
        }

        val repositoryConfiguration = repositoryConfigFile.takeIf { it.isFile }?.readValueOrNull()
            ?: RepositoryConfiguration()

        val analyzerConfig = repositoryConfiguration.analyzer?.let { analyzerConfigFromJob.merge(it) }
            ?: analyzerConfigFromJob

        val analyzer = Analyzer(analyzerConfig)

        val enabledPackageManagers = analyzerConfig.determineEnabledPackageManagers()

        logger.info(
            "Searching for definitions files of the following enabled package manager(s): " +
                    enabledPackageManagers.joinToString().ifEmpty { "<None>" }
        )

        val info = analyzer.findManagedFiles(inputDir, enabledPackageManagers, repositoryConfiguration)
        if (info.managedFiles.isEmpty()) {
            logger.warn("No definition files found.")
        } else {
            val filesPerManager = info.managedFiles.mapKeysTo(sortedMapOf()) { it.key.descriptor.displayName }
            var count = 0

            filesPerManager.forEach { (manager, files) ->
                count += files.size
                logger.info("Found ${files.size} $manager definition file(s) at:")

                files.forEach { file ->
                    val relativePath = file.toRelativeString(inputDir).takeIf { it.isNotEmpty() } ?: "."
                    logger.info("\t$relativePath")
                }
            }

            logger.info("Found $count definition file(s) from ${filesPerManager.size} package manager(s) in total.")
        }

        logger.info("Creating package curation providers...")

        val packageCurationProviders = buildList {
            add(
                ResolvedPackageCurations.REPOSITORY_CONFIGURATION_PROVIDER_ID to SimplePackageCurationProvider(
                    repositoryConfiguration.curations.packages
                )
            )

            val packageCurationProviderConfigs = config.packageCurationProviders.map { it.mapToOrt() }
            addAll(PackageCurationProviderFactory.create(packageCurationProviderConfigs))
        }

        logger.info("Starting analysis of definition file(s)...")

        val ortResult = analyzer.analyze(info, packageCurationProviders)

        val projectCount = ortResult.getProjects().size
        val packageCount = ortResult.getPackages().size
        logger.info(
            "Found $projectCount project(s) and $packageCount package(s) in total (not counting excluded ones)."
        )

        val curationCount = ortResult.getPackages().sumOf { it.curations.size }
        logger.info("Applied $curationCount curation(s) from 1 provider.")

        checkNotNull(ortResult.analyzer?.result) {
            "There was an error creating the analyzer result."
        }

        return ortResult
    }
}
