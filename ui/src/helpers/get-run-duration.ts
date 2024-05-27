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

export function calculateDuration(
  createdAt: string,
  finishedAt: string
): string {
  // Convert the timestamps to Date objects
  const createdAtDate = new Date(createdAt);
  const finishedAtDate = new Date(finishedAt);

  // Calculate the difference in milliseconds
  const durationMs = finishedAtDate.getTime() - createdAtDate.getTime();

  // Convert the duration from milliseconds to seconds
  const durationSec = Math.floor(durationMs / 1000);

  // Calculate hours, minutes, and seconds
  const hours = Math.floor(durationSec / 3600);
  const minutes = Math.floor((durationSec % 3600) / 60);
  const seconds = durationSec % 60;

  // Format the duration as "hours:minutes:seconds"
  const formattedDuration = `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;

  return formattedDuration;
}
