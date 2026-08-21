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

import { PackageURL } from 'packageurl-js';

import { Identifier } from '@/api';
import type { PackageIdType } from '@/schemas';

export function identifierToPurl(pkg: Identifier | undefined | null): string {
  if (!pkg) {
    return '';
  }
  const purl = new PackageURL(pkg.type, pkg.namespace, pkg.name, pkg.version);
  return purl.toString();
}

export function identifierToString(pkg: Identifier | undefined | null): string {
  if (!pkg) {
    return '';
  }
  const { type, namespace, name, version } = pkg;
  return `${type}:${namespace}:${name}:${version}`;
}

type PackageIdentifier = {
  packageId?: Identifier | null;
  purl?: string | null;
};

/** Return the package identifier matching the configured display format. */
export function getPackageIdString(
  pkg: PackageIdentifier,
  packageIdType: PackageIdType
): string {
  return packageIdType === 'PURL'
    ? (pkg.purl ?? '')
    : identifierToString(pkg.packageId);
}

/** Compare packages by the identifiers displayed in package columns. */
export function comparePackageIds(
  pkgA: PackageIdentifier,
  pkgB: PackageIdentifier,
  packageIdType: PackageIdType
): number {
  const idA = getPackageIdString(pkgA, packageIdType);
  const idB = getPackageIdString(pkgB, packageIdType);

  return idA.localeCompare(idB, undefined, {
    numeric: true,
    sensitivity: 'base',
  });
}
