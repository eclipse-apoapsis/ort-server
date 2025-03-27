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

function divmod(a: number, b: number): [number, number] {
  const remainder = a % b;
  return [(a - remainder) / b, remainder];
}

export function calculateDuration(
  createdAt: string,
  finishedAt: string
): { formattedDuration: string; durationMs: number } {
  // Convert the timestamps to Date objects
  const createdAtDate = new Date(createdAt);
  const finishedAtDate = new Date(finishedAt);

  // Calculate the difference in milliseconds
  const durationMs = finishedAtDate.getTime() - createdAtDate.getTime();

  const formattedDuration = convertDurationToHms(durationMs);

  return {
    formattedDuration: formattedDuration,
    durationMs: durationMs,
  };
}

export function convertDurationToHms(durationMs: number): string {
  // Convert the duration from milliseconds to seconds
  const [durationSec] = divmod(durationMs, 1000);

  // Calculate hours, minutes, and seconds
  const [durationMin, seconds] = divmod(durationSec, 60);
  const [durationHours, minutes] = divmod(durationMin, 60);
  const [days, hours] = divmod(durationHours, 24);

  // Format the duration as "<D>d <H>h <M>m <S>s", omitting zero values except:
  // - when the duration is 0 -> "0s"
  // - when minutes are 0 but hours or seconds are not -> "1h 0m 26s"
  const formattedDuration: string[] = [];

  if (days > 0) {
    formattedDuration.push(`${days}d`);
  }

  if (hours > 0 || (days > 0 && minutes > 0)) {
    formattedDuration.push(`${hours}h`);
  }

  if (minutes > 0 || (hours > 0 && seconds > 0)) {
    formattedDuration.push(`${minutes}m`);
  }

  if (seconds > 0 || (days == 0 && hours == 0 && minutes == 0)) {
    formattedDuration.push(`${seconds}s`);
  }

  return formattedDuration.join(' ');
}

if (import.meta.vitest) {
  const { it, expect } = import.meta.vitest;

  it('divmod', () => {
    expect(divmod(10, 3)).toStrictEqual([3, 1]);
    expect(divmod(8, 2)).toStrictEqual([4, 0]);
    expect(divmod(1, 1)).toStrictEqual([1, 0]);
    expect(divmod(1, 0)).toStrictEqual([NaN, NaN]);
    expect(divmod(-10, 3)).toStrictEqual([-3, -1]);
    expect(divmod(10, -3)).toStrictEqual([-3, 1]);
    expect(divmod(-10, -3)).toStrictEqual([3, -1]);
  });

  it('calculateDuration', () => {
    expect(
      calculateDuration('2024-06-11T13:07:45Z', '2024-06-11T13:08:15Z')
        .formattedDuration
    ).toBe('30s');
    expect(
      calculateDuration('2024-06-11T13:07:45Z', '2024-06-11T13:12:15Z')
        .formattedDuration
    ).toBe('4m 30s');
    expect(
      calculateDuration('2024-06-11T13:00:00Z', '2024-06-11T14:00:01Z')
        .formattedDuration
    ).toBe('1h 0m 1s');
    expect(
      calculateDuration('2024-06-11T13:00:00Z', '2024-06-22T14:42:01Z')
        .formattedDuration
    ).toBe('11d 1h 42m 1s');
  });
}
