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

package org.eclipse.apoapsis.ortserver.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.groupChoice
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long

import kotlinx.serialization.encodeToString

import org.eclipse.apoapsis.ortserver.client.OrtServerClient

class InfoCommand(private val config: OrtServerOptions) : SuspendingCliktCommand(name = "info") {
    private val getOrtRunOption by option(
        "--get-by",
        help = "Get ORT run by ID or index."
    ).convert { it.uppercase() }.groupChoice(
        "ID" to OrtRunByIdOptions(),
        "INDEX" to OrtRunByIndexOptions()
    ).required()

    override suspend fun run() {
        val client = OrtServerClient.create(config.toOrtServerClientConfig())

        val ortRun = when (val it = getOrtRunOption) {
            is OrtRunByIdOptions -> {
                client.runs.getOrtRun(it.ortRunId)
            }

            is OrtRunByIndexOptions -> {
                client.repositories.getOrtRun(it.repositoryId, it.ortRunIndex)
            }
        }

        echo(json.encodeToString(ortRun))
    }
}

sealed class OrtRunOptions(name: String) : OptionGroup(name)

class OrtRunByIdOptions : OrtRunOptions(name = "Get ORT run by ID") {
    val ortRunId by option("--id", help = "The ID of the ORT run.").long().required()
}

class OrtRunByIndexOptions : OrtRunOptions(name = "Get ORT run by index") {
    val repositoryId by option("--repository-id", help = "The ID of the repository.").long().required()
    val ortRunIndex by option("--index", help = "The index of the ORT run.").long().required()
}
