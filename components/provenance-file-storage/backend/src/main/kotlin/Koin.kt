/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.components.provenancefilestorage

import org.eclipse.apoapsis.ortserver.storage.Storage

import org.koin.core.qualifier.named
import org.koin.dsl.module

/** The qualifier for the [Storage] of file archives. */
val fileArchiveStorageQualifier = named(OrtServerFileArchiveStorage.STORAGE_TYPE)

/** The qualifier for the [Storage] of file lists. */
val fileListStorageQualifier = named(OrtServerFileListStorage.STORAGE_TYPE)

/** A Koin module providing the [Storage]s for file archives and file lists, and the [ProvenanceFileStorageService]. */
val provenanceFileStorageModule = module {
    single(fileArchiveStorageQualifier) { Storage.create(OrtServerFileArchiveStorage.STORAGE_TYPE, get(), get()) }
    single(fileListStorageQualifier) { Storage.create(OrtServerFileListStorage.STORAGE_TYPE, get(), get()) }
    single { ProvenanceFileStorageService(get(fileArchiveStorageQualifier), get(fileListStorageQualifier)) }
}
