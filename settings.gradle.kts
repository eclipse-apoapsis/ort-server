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

rootProject.name = "ort-server"

include(":api-v1")
include(":clients:keycloak")
include(":core")
include(":dao")
include(":model")
include(":orchestrator")
include(":secrets:spi")
include(":secrets:vault")
include(":services")
include(":storage:spi")
include(":transport:activemqartemis")
include(":transport:kubernetes")
include(":transport:kubernetes-jobmonitor")
include(":transport:rabbitmq")
include(":transport:spi")
include(":utils:config")
include(":workers:advisor")
include(":workers:analyzer")
include(":workers:common")
include(":workers:evaluator")
include(":workers:reporter")
include(":workers:scanner")

project(":secrets:spi").name = "secrets-spi"
project(":transport:spi").name = "transport-spi"
