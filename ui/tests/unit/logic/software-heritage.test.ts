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

it.each([
  {
    name: 'github purl with SHA revision',
    purl: 'pkg:github/owner/repo@abc123def456789012345678901234567890abcd',
    path: 'src/main.ts',
    startLine: 10,
    endLine: 20,
    expected:
      'https://archive.softwareheritage.org/browse/content/?origin_url=https%3A%2F%2Fgithub.com%2Fowner%2Frepo&path=src%2Fmain.ts&revision=abc123def456789012345678901234567890abcd#L10-L20',
  },
  {
    name: 'github with branch',
    purl: 'pkg:github/owner/repo@main',
    path: 'src/main.ts',
    startLine: 1,
    endLine: 5,
    expected:
      'https://archive.softwareheritage.org/browse/content/?origin_url=https%3A%2F%2Fgithub.com%2Fowner%2Frepo&path=src%2Fmain.ts&branch=main#L1-L5',
  },
  {
    name: 'github without namespace returns null',
    purl: 'pkg:github/repo@main',
    path: 'main.ts',
    startLine: 1,
    endLine: 5,
    expected: null,
  },
  {
    name: 'invalid purl returns null',
    purl: 'not-a-purl',
    path: 'main.ts',
    startLine: 1,
    endLine: 5,
    expected: null,
  },
  {
    name: 'maven package',
    purl: 'pkg:maven/org.apache.commons/commons-lang3@3.12.0',
    path: 'src/main/java/Foo.java',
    startLine: 5,
    endLine: 10,
    expected:
      'https://archive.softwareheritage.org/browse/content/?origin_url=https%3A%2F%2Frepo1.maven.org%2Fmaven2%2Forg%2Fapache%2Fcommons%2Fcommons-lang3&path=src%2Fmain%2Fjava%2FFoo.java&release=3.12.0#L5-L10',
  },
  {
    name: 'maven without namespace returns null',
    purl: 'pkg:maven/some-lib@1.0.0',
    path: 'Foo.java',
    startLine: 1,
    endLine: 5,
    expected: null,
  },
  {
    name: 'npm scoped package',
    purl: 'pkg:npm/%40types/node@18.0.0',
    path: 'index.d.ts',
    startLine: 1,
    endLine: 5,
    expected:
      'https://archive.softwareheritage.org/browse/content/?origin_url=https%3A%2F%2Fwww.npmjs.com%2Fpackage%2F%40types%2Fnode&path=index.d.ts&release=18.0.0#L1-L5',
  },
  {
    name: 'npm unscoped package',
    purl: 'pkg:npm/lodash@4.17.21',
    path: 'lodash.js',
    startLine: 1,
    endLine: 5,
    expected:
      'https://archive.softwareheritage.org/browse/content/?origin_url=https%3A%2F%2Fwww.npmjs.com%2Fpackage%2Flodash&path=lodash.js&release=4.17.21#L1-L5',
  },
  {
    name: 'pypi package',
    purl: 'pkg:pypi/requests@2.28.0',
    path: 'requests/__init__.py',
    startLine: 1,
    endLine: 10,
    expected:
      'https://archive.softwareheritage.org/browse/content/?origin_url=https%3A%2F%2Fpypi.org%2Fproject%2Frequests&path=requests%2F__init__.py&release=2.28.0#L1-L10',
  },
  {
    name: 'single-line finding uses plain #L fragment',
    purl: 'pkg:npm/apache-log2@1.1.0',
    path: 'package/README.md',
    startLine: 8,
    endLine: 8,
    expected:
      'https://archive.softwareheritage.org/browse/content/?origin_url=https%3A%2F%2Fwww.npmjs.com%2Fpackage%2Fapache-log2&path=package%2FREADME.md&release=1.1.0#L8',
  },
  {
    name: 'unsupported purl type returns null',
    purl: 'pkg:golang/github.com/user/repo@v1.0.0',
    path: 'main.go',
    startLine: 1,
    endLine: 5,
    expected: null,
  },
])(
  'buildSwhBrowseUrl: $name',
  ({ purl, path, startLine, endLine, expected }) => {
    expect(buildSwhBrowseUrl(purl, path, startLine, endLine)).toBe(expected);
  }
);
