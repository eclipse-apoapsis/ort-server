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

package org.eclipse.apoapsis.ortserver.components.authorization.service

import org.eclipse.apoapsis.ortserver.model.HierarchyId

/**
 * An exception class that is thrown by the [AuthorizationService] when it cannot resolve a [HierarchyId]. This
 * typically means that users have called the API with the ID of a non-existing hierarchy element. Thus, such exceptions
 * should lead to HTTP 404 responses.
 */
class InvalidHierarchyIdException(
    val hierarchyId: HierarchyId
) : RuntimeException("Could not resolve hierarchy ID: $hierarchyId.")
