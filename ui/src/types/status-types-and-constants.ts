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
  | 'FINISHED_WITH_ISSUES'
  | undefined;

// All vulnerability ratings
export type VulnerabilityRating =
  | 'CRITICAL'
  | 'HIGH'
  | 'MEDIUM'
  | 'LOW'
  | 'NONE';

// Note: all color classes need to be defined as they are here, as they are formed
// in compilation time and cannot be interpolated in runtime

// Map statuses to TailwindCSS font colors
export const STATUS_FONT_COLOR: { [K in Exclude<Status, undefined>]: string } =
  {
    CREATED: 'text-gray-500',
    SCHEDULED: 'text-blue-300',
    RUNNING: 'text-blue-500',
    ACTIVE: 'text-blue-500',
    FAILED: 'text-red-500',
    FINISHED: 'text-green-500',
    FINISHED_WITH_ISSUES: 'text-yellow-500',
  } as const;

// Map statuses to TailwindCSS background colors
export const STATUS_BACKGROUND_COLOR: {
  [K in Exclude<Status, undefined>]: string;
} = {
  CREATED: 'bg-gray-500',
  SCHEDULED: 'bg-blue-300',
  RUNNING: 'bg-blue-500',
  ACTIVE: 'bg-blue-500',
  FAILED: 'bg-red-500',
  FINISHED: 'bg-green-500',
  FINISHED_WITH_ISSUES: 'bg-yellow-500',
} as const;

// Map statuses to TailwindCSS classes
export const STATUS_CLASS: {
  [K in Exclude<Status, undefined>]: string;
} = {
  CREATED: 'w-3 h-3 rounded-full',
  SCHEDULED: 'w-3 h-3 rounded-full',
  RUNNING: 'w-4 h-4 rounded-full animate-pulse border border-black',
  ACTIVE: 'w-4 h-4 rounded-full animate-pulse border border-black',
  FAILED: 'w-3 h-3 rounded-full',
  FINISHED: 'w-3 h-3 rounded-full',
  FINISHED_WITH_ISSUES: 'w-3 h-3 rounded-full',
} as const;
