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

import { describe, expect, it } from 'vitest';

import { antGlobToRegex } from '@/lib/ant-glob';

const matches = (pattern: string, path: string) =>
  antGlobToRegex(pattern).test(path);

describe('antGlobToRegex', () => {
  it('matches any number of directories with "**"', () => {
    expect(matches('**/test/**', 'a/test/b/pom.xml')).toBe(true);
    expect(matches('**/test/**', 'test/pom.xml')).toBe(true);
    expect(matches('**/test/**', 'a/latest/pom.xml')).toBe(false);
    expect(matches('docs/**', 'docs')).toBe(true);
    expect(matches('a/**/b', 'a/b')).toBe(true);
    expect(matches('a/**/b', 'a/x/y/b')).toBe(true);
  });

  // The expected results are those of Spring's AntPathMatcher, which ORT uses.
  it('matches "**" within a path segment like "*"', () => {
    expect(matches('src/**.json', 'src/package.json')).toBe(true);
    expect(matches('src/**.json', 'src/a/package.json')).toBe(false);
    expect(matches('a**/b', 'ax/b')).toBe(true);
    expect(matches('a**/b', 'a/x/b')).toBe(false);
    expect(matches('a/b**', 'a/bc')).toBe(true);
    expect(matches('a/b**', 'a/b/c')).toBe(false);
  });

  it('matches within one directory with "*" and "?"', () => {
    expect(matches('src/*', 'src/a')).toBe(true);
    expect(matches('src/*', 'src/a/b')).toBe(false);
    expect(matches('module-?/pom.xml', 'module-a/pom.xml')).toBe(true);
  });

  it('ignores a leading "./" and matches other characters literally', () => {
    expect(matches('./docs/**', 'docs/index.md')).toBe(true);
    expect(matches('a.b/**', 'axb/pom.xml')).toBe(false);
  });
});
