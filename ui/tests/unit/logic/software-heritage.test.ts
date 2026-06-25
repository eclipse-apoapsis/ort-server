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

import { buildSwhBrowseUrl } from '@/lib/software-heritage';

it('buildSwhBrowseUrl: github purl with SHA revision', () => {
  expect(
    buildSwhBrowseUrl(
      'pkg:github/owner/repo@abc123def456789012345678901234567890abcd',
      'src/main.ts',
      10,
      20
    )
  ).toBe(
    'https://archive.softwareheritage.org/browse/content/?origin_url=https%3A%2F%2Fgithub.com%2Fowner%2Frepo&path=src%2Fmain.ts&revision=abc123def456789012345678901234567890abcd#L10-L20'
  );
});

it('buildSwhBrowseUrl: github with branch', () => {
  expect(
    buildSwhBrowseUrl('pkg:github/owner/repo@main', 'src/main.ts', 1, 5)
  ).toBe(
    'https://archive.softwareheritage.org/browse/content/?origin_url=https%3A%2F%2Fgithub.com%2Fowner%2Frepo&path=src%2Fmain.ts&branch=main#L1-L5'
  );
});

it('buildSwhBrowseUrl: github without namespace returns null', () => {
  expect(buildSwhBrowseUrl('pkg:github/repo@main', 'main.ts', 1, 5)).toBeNull();
});

it('buildSwhBrowseUrl: invalid purl returns null', () => {
  expect(buildSwhBrowseUrl('not-a-purl', 'main.ts', 1, 5)).toBeNull();
});

it('buildSwhBrowseUrl: maven package', () => {
  expect(
    buildSwhBrowseUrl(
      'pkg:maven/org.apache.commons/commons-lang3@3.12.0',
      'src/main/java/Foo.java',
      5,
      10
    )
  ).toBe(
    'https://archive.softwareheritage.org/browse/content/?origin_url=https%3A%2F%2Frepo1.maven.org%2Fmaven2%2Forg%2Fapache%2Fcommons%2Fcommons-lang3&path=src%2Fmain%2Fjava%2FFoo.java&release=3.12.0#L5-L10'
  );
});

it('buildSwhBrowseUrl: maven without namespace returns null', () => {
  expect(
    buildSwhBrowseUrl('pkg:maven/some-lib@1.0.0', 'Foo.java', 1, 5)
  ).toBeNull();
});

it('buildSwhBrowseUrl: npm scoped package', () => {
  expect(
    buildSwhBrowseUrl('pkg:npm/%40types/node@18.0.0', 'index.d.ts', 1, 5)
  ).toBe(
    'https://archive.softwareheritage.org/browse/content/?origin_url=https%3A%2F%2Fwww.npmjs.com%2Fpackage%2F%40types%2Fnode&path=index.d.ts&release=18.0.0#L1-L5'
  );
});

it('buildSwhBrowseUrl: npm unscoped package', () => {
  expect(buildSwhBrowseUrl('pkg:npm/lodash@4.17.21', 'lodash.js', 1, 5)).toBe(
    'https://archive.softwareheritage.org/browse/content/?origin_url=https%3A%2F%2Fwww.npmjs.com%2Fpackage%2Flodash&path=lodash.js&release=4.17.21#L1-L5'
  );
});

it('buildSwhBrowseUrl: pypi package', () => {
  expect(
    buildSwhBrowseUrl('pkg:pypi/requests@2.28.0', 'requests/__init__.py', 1, 10)
  ).toBe(
    'https://archive.softwareheritage.org/browse/content/?origin_url=https%3A%2F%2Fpypi.org%2Fproject%2Frequests&path=requests%2F__init__.py&release=2.28.0#L1-L10'
  );
});

it('buildSwhBrowseUrl: single-line finding uses plain #L fragment', () => {
  expect(
    buildSwhBrowseUrl('pkg:npm/apache-log2@1.1.0', 'package/README.md', 8, 8)
  ).toBe(
    'https://archive.softwareheritage.org/browse/content/?origin_url=https%3A%2F%2Fwww.npmjs.com%2Fpackage%2Fapache-log2&path=package%2FREADME.md&release=1.1.0#L8'
  );
});

it('buildSwhBrowseUrl: unsupported purl type returns null', () => {
  expect(
    buildSwhBrowseUrl('pkg:golang/github.com/user/repo@v1.0.0', 'main.go', 1, 5)
  ).toBeNull();
});
