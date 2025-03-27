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

export function extractInitials(
  fullName: string | undefined
): string | undefined {
  // Remove non-alphanumeric characters.
  const strippedName = (fullName ?? '').replace(
    /[^\p{Letter}\p{Number}\p{Separator}]/gu,
    ''
  );

  // Split words, omitting empty strings.
  const words = strippedName.split(/\p{Separator}/u).filter(Boolean);

  const firstName = words.at(0);
  if (!firstName || !firstName[0]) return undefined;

  const firstInitial = firstName[0].toUpperCase();
  if (words.length == 1) return firstInitial;

  const lastName = words[words.length - 1];
  if (!lastName || !lastName[0]) return firstInitial;

  const lastInitial = lastName[0].toUpperCase();
  return firstInitial + lastInitial;
}

if (import.meta.vitest) {
  const { it, expect } = import.meta.vitest;

  it('extractInitials', () => {
    expect(extractInitials('John')).toBe('J');
    expect(extractInitials('John Doe')).toBe('JD');
    expect(extractInitials('John A. Doe')).toBe('JD');

    expect(extractInitials('lower case')).toBe('LC');
    expect(extractInitials('Seven of 9')).toBe('S9');
    expect(extractInitials('Über ätzend')).toBe('ÜÄ');
    expect(extractInitials(' with  spaces   everywhere    ')).toBe('WE');

    expect(extractInitials(undefined)).toBe(undefined);
    expect(extractInitials('')).toBe(undefined);
    expect(extractInitials('??')).toBe(undefined);
  });
}
