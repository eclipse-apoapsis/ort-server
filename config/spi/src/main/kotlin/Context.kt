/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

/**
 * A class representing a context for querying the application configuration.
 *
 * When querying the configuration of ORT Server, clients have to provide a context, which uniquely defines the
 * configuration to be used. The exact meaning is specific to a concrete implementation of the service provider
 * interface, but in general, via the context a set of configuration properties can be selected if there are multiple
 * such sets. For instance, if the configuration is stored in a version control system, the context could be the
 * revision of the configuration to be checked out.
 */
@JvmInline
value class Context(val name: String)
