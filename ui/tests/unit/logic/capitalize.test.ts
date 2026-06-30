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

import { expect, it } from 'vitest';

import { capitalize } from '@/helpers/capitalize';

it.each([
  { name: 'lowercase word', input: 'hello', expected: 'Hello' },
  { name: 'mixed case', input: 'hELLO', expected: 'Hello' },
  { name: 'all uppercase', input: 'WORLD', expected: 'World' },
  { name: 'already capitalized', input: 'TypeScript', expected: 'Typescript' },
  { name: 'single character lowercase', input: 'a', expected: 'A' },
  { name: 'single character uppercase', input: 'Z', expected: 'Z' },
  { name: 'empty string', input: '', expected: '' },
  { name: 'string with spaces', input: 'hello world', expected: 'Hello world' },
  { name: 'string with numbers', input: '123abc', expected: '123abc' },
  {
    name: 'string starting with special character',
    input: '!important',
    expected: '!important',
  },
])('capitalize - $name', ({ input, expected }) => {
  expect(capitalize(input)).toBe(expected);
});
