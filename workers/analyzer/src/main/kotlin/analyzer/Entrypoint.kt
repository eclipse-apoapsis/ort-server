/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.analyzer

import java.io.File

import org.ossreviewtoolkit.analyzer.managers.Npm
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration

fun main() {
    // This is the entry point of the Analyzer Docker image. It calls the Analyzer from ORT programmatically by
    // interfacing on its APIs.
    println("Hello World")

    // This tests that ORT's classes can be accessed as well as the CLI tools of the Docker image.
    val npm = Npm.Factory().create(File("."), AnalyzerConfiguration(), RepositoryConfiguration())
    val version = npm.getVersion()
    println("Npm version is $version.")
}
