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

import { PackageURL } from 'packageurl-js';

const SWH_BROWSE_BASE = 'https://archive.softwareheritage.org/browse/content/';

/**
 * Map a parsed purl to the canonical origin URL used by Software Heritage.
 * Return null for unsupported package types or missing required fields.
 */
function purlToSwhOriginUrl(pkg: PackageURL): string | null {
  switch (pkg.type) {
    case 'github':
      // pkg:github/owner/repo — namespace is owner, name is repo.
      return pkg.namespace && pkg.name
        ? `https://github.com/${pkg.namespace}/${pkg.name}`
        : null;
    case 'npm': {
      // pkg:npm/%40scope/name or pkg:npm/name.
      const name = pkg.namespace ? `${pkg.namespace}/${pkg.name}` : pkg.name;
      return `https://www.npmjs.com/package/${name}`;
    }
    case 'pypi':
      return `https://pypi.org/project/${pkg.name}`;
    case 'maven':
      // SWH archives Maven Central; namespace is groupId with dots.
      if (pkg.namespace) {
        const groupPath = pkg.namespace.replace(/\./g, '/');
        return `https://repo1.maven.org/maven2/${groupPath}/${pkg.name}`;
      }
      return null;
    default:
      return null;
  }
}

/**
 * Return the SWH browse URL version parameter for a parsed purl.
 *
 * Package archives (npm, PyPI, Maven) are indexed as "releases" in SWH.
 * GitHub refs that look like a 40-char hex SHA map to "revision"; everything
 * else (tags, branch names) maps to "branch".
 */
function swhVersionParam(pkg: PackageURL): Record<string, string> {
  if (!pkg.version) return {};

  if (pkg.type === 'github') {
    const isSha = /^[0-9a-f]{40}$/i.test(pkg.version);
    return isSha ? { revision: pkg.version } : { branch: pkg.version };
  }

  return { release: pkg.version };
}

/**
 * Build a Software Heritage browse URL for a specific file and line range.
 * Return null if the purl type is not supported.
 */
export function buildSwhBrowseUrl(
  purl: string,
  path: string,
  startLine: number,
  endLine: number
): string | null {
  let pkg: PackageURL;
  try {
    pkg = PackageURL.fromString(purl);
  } catch {
    return null;
  }

  const originUrl = purlToSwhOriginUrl(pkg);
  if (!originUrl) return null;

  const params = new URLSearchParams({
    origin_url: originUrl,
    path,
    ...swhVersionParam(pkg),
  });
  return `${SWH_BROWSE_BASE}?${params.toString()}#L${startLine}-L${endLine}`;
}

if (import.meta.vitest) {
  const { it, expect } = import.meta.vitest;

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

  it('buildSwhBrowseUrl: github purl with branch', () => {
    expect(
      buildSwhBrowseUrl('pkg:github/owner/repo@main', 'src/main.ts', 1, 5)
    ).toBe(
      'https://archive.softwareheritage.org/browse/content/?origin_url=https%3A%2F%2Fgithub.com%2Fowner%2Frepo&path=src%2Fmain.ts&branch=main#L1-L5'
    );
  });

  it('buildSwhBrowseUrl: npm unscoped package', () => {
    expect(buildSwhBrowseUrl('pkg:npm/lodash@4.17.21', 'lodash.js', 1, 5)).toBe(
      'https://archive.softwareheritage.org/browse/content/?origin_url=https%3A%2F%2Fwww.npmjs.com%2Fpackage%2Flodash&path=lodash.js&release=4.17.21#L1-L5'
    );
  });

  it('buildSwhBrowseUrl: npm scoped package', () => {
    expect(
      buildSwhBrowseUrl('pkg:npm/%40types/node@18.0.0', 'index.d.ts', 1, 1)
    ).toBe(
      'https://archive.softwareheritage.org/browse/content/?origin_url=https%3A%2F%2Fwww.npmjs.com%2Fpackage%2F%40types%2Fnode&path=index.d.ts&release=18.0.0#L1-L1'
    );
  });

  it('buildSwhBrowseUrl: pypi package', () => {
    expect(
      buildSwhBrowseUrl(
        'pkg:pypi/requests@2.28.0',
        'requests/__init__.py',
        1,
        10
      )
    ).toBe(
      'https://archive.softwareheritage.org/browse/content/?origin_url=https%3A%2F%2Fpypi.org%2Fproject%2Frequests&path=requests%2F__init__.py&release=2.28.0#L1-L10'
    );
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

  it('buildSwhBrowseUrl: unsupported purl type returns null', () => {
    expect(
      buildSwhBrowseUrl(
        'pkg:golang/github.com/user/repo@v1.0.0',
        'main.go',
        1,
        5
      )
    ).toBeNull();
  });

  it('buildSwhBrowseUrl: invalid purl returns null', () => {
    expect(buildSwhBrowseUrl('not-a-purl', 'main.ts', 1, 5)).toBeNull();
  });

  it('buildSwhBrowseUrl: maven without namespace returns null', () => {
    expect(
      buildSwhBrowseUrl('pkg:maven/some-lib@1.0.0', 'Foo.java', 1, 5)
    ).toBeNull();
  });

  it('buildSwhBrowseUrl: github without namespace returns null', () => {
    expect(
      buildSwhBrowseUrl('pkg:github/repo@main', 'main.ts', 1, 5)
    ).toBeNull();
  });
}
