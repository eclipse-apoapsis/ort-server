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

import {
  clearDetectedLicenseMarkers,
  clearPackageMarker,
  getDetectedLicenseQueryFilter,
  getMarkerExpandedState,
  getPackageIdentifierQueryFilter,
} from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/license-findings/-components/license-findings-state';

describe('license findings deep-link state', () => {
  it('uses a marked license instead of the interactive filter', () => {
    const filter = getDetectedLicenseQueryFilter(
      'MIT',
      'MIT,MIT AND Apache-2.0'
    );

    expect(filter).toEqual({ license: 'MIT' });
    expect(filter).not.toHaveProperty('licenseMatchType');
  });

  it('keeps comma-separated interactive license filters', () => {
    const filter = getDetectedLicenseQueryFilter(undefined, 'MIT,Apache-2.0');

    expect(filter).toEqual({ license: 'MIT,Apache-2.0' });
    expect(filter).not.toHaveProperty('licenseMatchType');
  });

  it('uses stable marker values as expanded row IDs', () => {
    expect(getMarkerExpandedState('MIT AND Apache-2.0')).toEqual({
      'MIT AND Apache-2.0': true,
    });

    expect(getMarkerExpandedState('Maven:com.example:library:1.0')).toEqual({
      'Maven:com.example:library:1.0': true,
    });
  });

  it('uses the exact ORT identifier in PURL mode', () => {
    const identifier = 'Maven:com.example:library:1.0';

    expect(
      getPackageIdentifierQueryFilter(identifier, 'PURL', 'pkg:maven/example')
    ).toEqual({ identifier, identifierMatchType: 'exact' });
  });

  it('uses the exact project identifier even when it has no PURL', () => {
    const identifier = 'Gradle:com.example:project:1.0';

    expect(
      getPackageIdentifierQueryFilter(identifier, 'PURL', undefined)
    ).toEqual({ identifier, identifierMatchType: 'exact' });
  });

  it('updates expansion when a marker changes and collapses without one', () => {
    expect(getMarkerExpandedState('Apache-2.0')).toEqual({
      'Apache-2.0': true,
    });
    expect(getMarkerExpandedState('MIT')).toEqual({ MIT: true });
    expect(getMarkerExpandedState()).toEqual({});
  });

  it('clears both markers for top-level interactions', () => {
    expect(
      clearDetectedLicenseMarkers({
        detectedLicense: ['MIT'],
        marked: 'MIT',
        packageMarked: 'Maven:com.example:library:1.0',
        page: 2,
      })
    ).toEqual({
      detectedLicense: ['MIT'],
      marked: undefined,
      packageMarked: undefined,
      page: 2,
    });
  });

  it('clears only the package marker for nested interactions', () => {
    expect(
      clearPackageMarker({
        marked: 'MIT',
        packageMarked: 'Maven:com.example:library:1.0',
        packagePage: 2,
      })
    ).toEqual({
      marked: 'MIT',
      packageMarked: undefined,
      packagePage: 2,
    });
  });

  it('keeps normal identifier filters when no package is marked', () => {
    expect(
      getPackageIdentifierQueryFilter(undefined, 'PURL', 'pkg:maven/example')
    ).toEqual({ purl: 'pkg:maven/example' });
    expect(
      getPackageIdentifierQueryFilter(undefined, 'ORT_ID', 'Maven:example')
    ).toEqual({ identifier: 'Maven:example' });
  });
});
