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

/**
 * Capitalizes the first letter of a string and converts the rest to lowercase.
 * @param str - The input string to capitalize
 * @returns The capitalized string
 */
export function capitalize(str: string): string {
  if (str.length === 0) {
    return str;
  }
  return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
}

// Unit tests.

if (import.meta.vitest) {
  const { it, expect } = import.meta.vitest;

  it('capitalize - lowercase word', () => {
    expect(capitalize('hello')).toBe('Hello');
  });

  it('capitalize - mixed case', () => {
    expect(capitalize('hELLO')).toBe('Hello');
  });

  it('capitalize - all uppercase', () => {
    expect(capitalize('WORLD')).toBe('World');
  });

  it('capitalize - already capitalized', () => {
    expect(capitalize('TypeScript')).toBe('Typescript');
  });

  it('capitalize - single character lowercase', () => {
    expect(capitalize('a')).toBe('A');
  });

  it('capitalize - single character uppercase', () => {
    expect(capitalize('Z')).toBe('Z');
  });

  it('capitalize - empty string', () => {
    expect(capitalize('')).toBe('');
  });

  it('capitalize - string with spaces', () => {
    expect(capitalize('hello world')).toBe('Hello world');
  });

  it('capitalize - string with numbers', () => {
    expect(capitalize('123abc')).toBe('123abc');
  });

  it('capitalize - string starting with special character', () => {
    expect(capitalize('!important')).toBe('!important');
  });
}
