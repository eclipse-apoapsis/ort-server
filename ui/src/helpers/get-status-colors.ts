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

import {
  Status,
  STATUS_BACKGROUND_COLOR,
  STATUS_CLASS,
  STATUS_FONT_COLOR,
} from '@/types/status-types-and-constants';

// Get the color class for font coloring
export function getStatusFontColor(status: Status): string {
  if (status === undefined) {
    return 'text-gray-300'; // Define the color for undefined status here
  }
  return STATUS_FONT_COLOR[status]; // This will now only be called for defined statuses
}

// Get the color class for coloring the background of elements
export function getStatusBackgroundColor(status: Status): string {
  if (status === undefined) {
    return 'bg-gray-300'; // Define the color for undefined status here
  }
  return STATUS_BACKGROUND_COLOR[status]; // This will now only be called for defined statuses
}

// Get the general class for the elements
export function getStatusClass(status: Status): string {
  if (status === undefined) {
    return 'w-3 h-3 rounded-full'; // Define the class for undefined status here
  }
  return STATUS_CLASS[status]; // This will now only be called for defined statuses
}
