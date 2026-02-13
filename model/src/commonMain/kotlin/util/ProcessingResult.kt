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

package org.eclipse.apoapsis.ortserver.model.util

/**
 * An enumeration that defines the different success statuses of an operation that processes a collection of items.
 */
enum class ProcessingResultStatus {
    /** The operation was completely successful, i.e., all items were processed successfully. */
    SUCCESS,

    /** The operation was partially successful, i.e., some items were processed successfully while others failed. */
    PARTIAL_SUCCESS,

    /** The operation failed completely, i.e., all items failed during processing. */
    FAILURE
}

/**
 * A data class to represent the result of an operation that processes a collection of items. It contains information
 * about the total number of items processed and the number of items that failed during processing. This class
 * provides an easy means to find out whether an operation was completely successful, partially successful, or
 * completely failed.
 */
data class ProcessingResult(
    /** The total number of items that were processed. */
    val totalCount: Int,

    /** The number of items that failed during processing. */
    val failedCount: Int = 0
) {
    /** The number of items that were successfully processed. */
    val successCount: Int
        get() = totalCount - failedCount

    /** The status of the processing operation based on the success and failure counts. */
    val status: ProcessingResultStatus
        get() = when (failedCount) {
            0 -> ProcessingResultStatus.SUCCESS
            totalCount -> ProcessingResultStatus.FAILURE
            else -> ProcessingResultStatus.PARTIAL_SUCCESS
        }
}
