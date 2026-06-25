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
  const fragment =
    startLine === endLine ? `L${startLine}` : `L${startLine}-L${endLine}`;

  return `${SWH_BROWSE_BASE}?${params.toString()}#${fragment}`;
}
