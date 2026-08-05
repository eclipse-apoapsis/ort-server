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

import { expect, it } from 'vitest';

import { escapeRegex } from '@/lib/regex';

it.each([
  { name: 'dot', input: '.', expected: '\\.' },
  { name: 'asterisk', input: '*', expected: '\\*' },
  { name: 'plus', input: '+', expected: '\\+' },
  { name: 'question mark', input: '?', expected: '\\?' },
  { name: 'caret', input: '^', expected: '\\^' },
  { name: 'dollar', input: '$', expected: '\\$' },
  { name: 'opening brace', input: '{', expected: '\\{' },
  { name: 'closing brace', input: '}', expected: '\\}' },
  { name: 'opening parenthesis', input: '(', expected: '\\(' },
  { name: 'closing parenthesis', input: ')', expected: '\\)' },
  { name: 'pipe', input: '|', expected: '\\|' },
  { name: 'opening bracket', input: '[', expected: '\\[' },
  { name: 'closing bracket', input: ']', expected: '\\]' },
  { name: 'backslash', input: '\\', expected: '\\\\' },
  {
    name: 'all metacharacters at once',
    input: '.*+?^${}()|[]\\',
    expected: '\\.\\*\\+\\?\\^\\$\\{\\}\\(\\)\\|\\[\\]\\\\',
  },
  { name: 'empty string', input: '', expected: '' },
  { name: 'plain word', input: 'ort-server', expected: 'ort-server' },
  {
    name: 'plain text with spaces',
    input: 'The ORT Server',
    expected: 'The ORT Server',
  },
  {
    name: 'metacharacters mixed into plain text',
    input: 'org.apache (core)',
    expected: 'org\\.apache \\(core\\)',
  },
])('escapeRegex - $name', ({ input, expected }) => {
  expect(escapeRegex(input)).toBe(expected);
});

it('escapes input so that it matches itself literally', () => {
  const input = 'a.b*c(d)[e]';

  expect(new RegExp(escapeRegex(input)).test(input)).toBe(true);
  expect(new RegExp(escapeRegex(input)).test('axbxcd e')).toBe(false);
});
