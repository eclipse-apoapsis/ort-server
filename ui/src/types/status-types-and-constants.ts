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

// All statuses reported either by ORT Runs or the individual jobs within them
export type Status =
  | 'CREATED'
  | 'SCHEDULED'
  | 'RUNNING'
  | 'ACTIVE'
  | 'FAILED'
  | 'FINISHED'
  | undefined;

// Note: all color classes need to be defined as they are here, as they are formed
// in compilation time and cannot be interpolated in runtime

// Map statuses to TailwindCSS font colors
export const STATUS_FONT_COLOR: { [K in Exclude<Status, undefined>]: string } =
  {
    CREATED: 'text-gray-500',
    SCHEDULED: 'text-yellow-500',
    RUNNING: 'text-blue-500',
    ACTIVE: 'text-blue-500',
    FAILED: 'text-red-500',
    FINISHED: 'text-green-500',
  } as const;

// Map statuses to TailwindCSS background colors
export const STATUS_BACKGROUND_COLOR: {
  [K in Exclude<Status, undefined>]: string;
} = {
  CREATED: 'bg-gray-500',
  SCHEDULED: 'bg-yellow-500',
  RUNNING: 'bg-blue-500',
  ACTIVE: 'bg-blue-500',
  FAILED: 'bg-red-500',
  FINISHED: 'bg-green-500',
} as const;
