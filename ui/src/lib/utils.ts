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

import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatTimestamp(
  timestamp: string,
  timeZone: string | undefined = undefined,
  locales: Intl.LocalesArgument = undefined
) {
  return new Date(timestamp).toLocaleString(locales, {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    timeZone: timeZone,
  });
}

if (import.meta.vitest) {
  const { it, expect } = import.meta.vitest;

  it('formatTimestamp', () => {
    expect(formatTimestamp('2024-06-11T13:07:45Z', 'UTC', 'en-US')).toBe(
      '06/11/2024, 01:07:45 PM'
    );

    expect(formatTimestamp('2024-06-11T13:07:45Z', 'UTC', 'de-DE')).toBe(
      '11.06.2024, 13:07:45'
    );

    expect(formatTimestamp('2024-06-11T13:07:45Z', 'UTC', 'fi-FI')).toBe(
      '11.06.2024 klo 13.07.45'
    );
  });
}
