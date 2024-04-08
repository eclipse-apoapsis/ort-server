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

package org.eclipse.apoapsis.ortserver.model.validation

/**
 * An object containing constraints for validation.
 */
@Suppress("MaxLineLength")
object Constraints {
    const val NAME_PATTERN_REGEX = "^(?! )[A-Za-z0-9- ]*(?<! )$"
    const val NAME_PATTERN_MESSAGE = "The entity name may only contain letters, numbers, hyphen marks and spaces. Leading and trailing spaces are not allowed."
}
