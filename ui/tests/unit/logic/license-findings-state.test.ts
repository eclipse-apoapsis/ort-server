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

import {
  defaultParseSearch,
  defaultStringifySearch,
} from '@tanstack/react-router';
import { describe, expect, it } from 'vitest';

import {
  canonicalizeLicenseTables,
  clearDetectedLicenseMarkers,
  clearPackageMarker,
  getDetectedLicenseQueryFilter,
  getLicenseFindingsTableState,
  getLicensePackagesExpandedState,
  getLicensePackagesQueryFilter,
  getLicensePackagesTableState,
  getLicenseTablePagination,
  getLicenseTablesExpandedState,
  getMarkerExpandedState,
  getPackageIdentifierQueryFilter,
  normalizeLicenseFindingsSearch,
  resetLicensePackageIdentifierControls,
  toggleLicensePackageTable,
  toggleLicenseTable,
  updateLicenseFindingsTable,
  updateLicensePackagesTable,
} from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/license-findings/-components/license-findings-state';
import {
  licenseFindingsSearchParameterSchema,
  packageIdTypeSchema,
  type LicenseFindingsSearchParameters,
  type LicensePackagesTableState,
} from '@/schemas';

const identifier = 'Maven:com.example:some library:1.0/rc%1';
const siblingIdentifier = 'NPM::example:2.0';
const makeSearch = (): LicenseFindingsSearchParameters => ({
  page: 2,
  detectedLicense: ['MIT', 'Apache-2.0'],
  licenseTables: {
    MIT: {
      page: 3,
      pageSize: 20,
      packages: {
        [identifier]: { page: 4, pageSize: 20 },
        [siblingIdentifier]: { page: 2 },
      },
    },
    'Apache-2.0': { page: 2, packages: { [identifier]: { page: 5 } } },
  },
});

const parse = (value: unknown) =>
  canonicalizeLicenseTables(licenseFindingsSearchParameterSchema.parse(value));

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
      getPackageIdentifierQueryFilter(
        identifier,
        packageIdTypeSchema.enum.PURL,
        'pkg:maven/example'
      )
    ).toEqual({ identifier, identifierMatchType: 'exact' });
  });

  it('uses the exact project identifier even when it has no PURL', () => {
    const identifier = 'Gradle:com.example:project:1.0';

    expect(
      getPackageIdentifierQueryFilter(
        identifier,
        packageIdTypeSchema.enum.PURL,
        undefined
      )
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
      getPackageIdentifierQueryFilter(
        undefined,
        packageIdTypeSchema.enum.PURL,
        'pkg:maven/example'
      )
    ).toEqual({ purl: 'pkg:maven/example' });
    expect(
      getPackageIdentifierQueryFilter(
        undefined,
        packageIdTypeSchema.enum.ORT_ID,
        'Maven:example'
      )
    ).toEqual({ identifier: 'Maven:example' });
  });
});

describe('scoped license table schemas', () => {
  it('preserves open default entries while pruning default controls and containers', () => {
    expect(
      parse({
        licenseTables: {
          MIT: {
            page: 1,
            pageSize: 10,
            sortBy: [],
            packages: { [identifier]: { page: 1, pageSize: 10 } },
          },
          'Apache-2.0': { packages: {} },
        },
      })
    ).toEqual({
      licenseTables: {
        MIT: { packages: { [identifier]: {} } },
        'Apache-2.0': {},
      },
    });
    expect(parse({ licenseTables: {} })).toEqual({});
  });

  it('discards malformed entries without losing valid siblings or valid controls', () => {
    expect(
      parse({
        licenseTables: {
          MIT: {
            page: -1,
            pageSize: 20,
            sortBy: [{ id: 'unsupported', desc: false }],
            packages: {
              [identifier]: { page: 3, pageSize: 0 },
              bad: null,
              array: [],
              string: 'bad',
            },
          },
          'Apache-2.0': {},
          bad: null,
          array: [],
        },
      })
    ).toEqual({
      licenseTables: {
        MIT: { pageSize: 20, packages: { [identifier]: { page: 3 } } },
        'Apache-2.0': {},
      },
    });
  });

  it.each([0, -1, 1.5, '2', null, Infinity, NaN])(
    'ignores invalid pagination %s',
    (page) => {
      expect(
        parse({ licenseTables: { MIT: { page, pageSize: page } } })
      ).toEqual({ licenseTables: { MIT: {} } });
    }
  );

  it.each([null, [], 'bad', 42])(
    'ignores a malformed hierarchy %s',
    (licenseTables) => {
      expect(parse({ licenseTables, page: 2 })).toEqual({ page: 2 });
    }
  );

  it('round-trips exact license and identifier keys through the router serializer', () => {
    const licenses = [
      'MIT AND (Apache-2.0 OR LicenseRef-a/b% c)',
      '__proto__',
      'constructor',
      'toString',
    ];
    const search = {
      licenseTables: Object.fromEntries(
        licenses.map((license) => [
          license,
          {
            packages: Object.fromEntries(
              [identifier, '__proto__', 'constructor', 'toString'].map((id) => [
                id,
                { page: 3 },
              ])
            ),
          },
        ])
      ),
    };
    const restored = parse(defaultParseSearch(defaultStringifySearch(search)));
    expect(restored).toEqual(search);
    for (const license of licenses) {
      expect(getLicenseTablesExpandedState(restored)).toHaveProperty(
        [license],
        true
      );
      expect(getLicensePackagesExpandedState(restored, license)).toHaveProperty(
        '__proto__',
        true
      );
      expect(Object.getPrototypeOf(restored.licenseTables)).toBe(
        Object.prototype
      );
    }
  });
});

describe('independent expansion and table controls', () => {
  it('derives expansion and zero-based pagination from scoped state', () => {
    const search = makeSearch();
    expect(getLicenseTablesExpandedState(search)).toEqual({
      MIT: true,
      'Apache-2.0': true,
    });
    expect(getLicensePackagesExpandedState(search, 'MIT')).toEqual({
      [identifier]: true,
      [siblingIdentifier]: true,
    });
    expect(
      getLicenseTablePagination(getLicensePackagesTableState(search, 'MIT'))
    ).toEqual({ pageIndex: 2, pageSize: 20 });
    expect(
      getLicenseTablePagination(
        getLicenseFindingsTableState(search, 'MIT', identifier)
      )
    ).toEqual({ pageIndex: 3, pageSize: 20 });
    expect(
      getLicenseTablePagination(
        getLicenseFindingsTableState(search, 'missing', identifier)
      )
    ).toEqual({ pageIndex: 0, pageSize: 10 });
  });

  it('deletes a closed license subtree and reopens at defaults without changing siblings', () => {
    const original = makeSearch();
    const closed = toggleLicenseTable(
      original,
      'MIT',
      packageIdTypeSchema.enum.PURL
    );
    expect(closed.licenseTables).toEqual({
      'Apache-2.0': original.licenseTables?.['Apache-2.0'],
    });
    expect(defaultStringifySearch(closed)).not.toBe(
      defaultStringifySearch(original)
    );
    const reopened = toggleLicenseTable(
      closed,
      'MIT',
      packageIdTypeSchema.enum.PURL
    );
    expect(reopened).toEqual({
      ...original,
      licenseTables: { ...closed.licenseTables, MIT: {} },
    });
    expect(original).toEqual(makeSearch());
    expect(
      toggleLicenseTable(
        { licenseTables: { MIT: {} } },
        'MIT',
        packageIdTypeSchema.enum.ORT_ID
      )
    ).toEqual({});
  });

  it('deletes only the closed package and reopens findings at defaults', () => {
    const original = makeSearch();
    const closed = toggleLicensePackageTable(original, 'MIT', identifier);
    expect(closed.licenseTables?.MIT).toEqual({
      page: 3,
      pageSize: 20,
      packages: { [siblingIdentifier]: { page: 2 } },
    });
    expect(closed.licenseTables?.['Apache-2.0']).toEqual(
      original.licenseTables?.['Apache-2.0']
    );
    const reopened = toggleLicensePackageTable(closed, 'MIT', identifier);
    expect(getLicenseFindingsTableState(reopened, 'MIT', identifier)).toEqual(
      {}
    );
    expect(original).toEqual(makeSearch());
  });

  it('updates the same package under separate licenses independently', () => {
    const original = makeSearch();
    const first = updateLicenseFindingsTable(original, 'MIT', identifier, {
      page: 8,
    });
    const second = updateLicenseFindingsTable(first, 'Apache-2.0', identifier, {
      page: 6,
    });
    expect(getLicenseFindingsTableState(second, 'MIT', identifier)).toEqual({
      page: 8,
      pageSize: 20,
    });
    expect(
      getLicenseFindingsTableState(second, 'Apache-2.0', identifier)
    ).toEqual({ page: 6 });
    const resized = updateLicenseFindingsTable(second, 'MIT', identifier, {
      pageSize: 30,
    });
    expect(getLicenseFindingsTableState(resized, 'MIT', identifier)).toEqual({
      pageSize: 30,
    });
    expect(getLicensePackagesTableState(resized, 'MIT').page).toBe(3);
    expect(original).toEqual(makeSearch());
  });

  it('resets only the owning package table for page size, filter and sorting changes', () => {
    const original = makeSearch();
    const changes = [
      { pageSize: 30 },
      {
        packageId: 'pkg:maven/example',
        packageIdType: packageIdTypeSchema.enum.PURL,
      },
      { sortBy: [{ id: 'identifier' as const, desc: true }] },
    ];
    for (const change of changes) {
      const updated = updateLicensePackagesTable(original, 'MIT', change);
      expect(getLicensePackagesTableState(updated, 'MIT')).toEqual({
        ...original.licenseTables?.MIT,
        ...change,
        page: undefined,
      });
      expect(updated.licenseTables?.['Apache-2.0']).toEqual(
        original.licenseTables?.['Apache-2.0']
      );
      expect(updated.page).toBe(2);
    }
    const paged = updateLicensePackagesTable(original, 'MIT', { page: 6 });
    expect(getLicensePackagesTableState(paged, 'MIT').page).toBe(6);
    expect(original).toEqual(makeSearch());
  });

  it('resets identifier-dependent controls in every license without closing tables', () => {
    const makeFiltered = (): LicenseFindingsSearchParameters => ({
      page: 2,
      marked: 'MIT',
      licenseTables: {
        MIT: {
          page: 3,
          pageSize: 20,
          packageId: 'library',
          packageIdType: packageIdTypeSchema.enum.ORT_ID,
          sortBy: [{ id: 'identifier', desc: true }],
          packages: { [identifier]: { page: 4 } },
        },
        'Apache-2.0': { packageMarked: identifier },
        'LicenseRef-open': {},
      },
    });
    const original = makeFiltered();
    const reset = resetLicensePackageIdentifierControls(original);

    // Expansions and findings pagination survive; only identifier-typed inputs go.
    expect(reset).toEqual({
      page: 2,
      marked: 'MIT',
      licenseTables: {
        MIT: { pageSize: 20, packages: { [identifier]: { page: 4 } } },
        'Apache-2.0': {},
        'LicenseRef-open': {},
      },
    });
    expect(original).toEqual(makeFiltered());
  });

  it('leaves a search without scoped tables untouched', () => {
    expect(
      resetLicensePackageIdentifierControls({ page: 2, packageId: 'library' })
    ).toEqual({ page: 2, packageId: 'library' });
  });

  it('does not reopen a closed subtree through a stale control update', () => {
    expect(updateLicensePackagesTable({}, 'MIT', { page: 2 })).toEqual({});
    expect(
      updateLicenseFindingsTable({}, 'MIT', identifier, { page: 2 })
    ).toEqual({});
    expect(toggleLicensePackageTable({}, 'MIT', identifier)).toEqual({});
  });

  it('treats prototype names as own keys, never as inherited entries', () => {
    expect(getLicensePackagesTableState({}, 'constructor')).toEqual({});
    expect(getLicensePackagesExpandedState({}, '__proto__')).toEqual({});
    const opened = toggleLicenseTable(
      {},
      '__proto__',
      packageIdTypeSchema.enum.ORT_ID
    );
    const packageOpened = toggleLicensePackageTable(
      opened,
      '__proto__',
      '__proto__'
    );
    const updated = updateLicenseFindingsTable(
      packageOpened,
      '__proto__',
      '__proto__',
      { page: 7 }
    );
    expect(
      getLicenseFindingsTableState(updated, '__proto__', '__proto__')
    ).toEqual({ page: 7 });
    expect(
      toggleLicensePackageTable(updated, '__proto__', '__proto__')
    ).toEqual({ licenseTables: { ['__proto__']: {} } });
    expect(
      toggleLicenseTable(updated, '__proto__', packageIdTypeSchema.enum.ORT_ID)
    ).toEqual({});
    expect(Object.getPrototypeOf(updated.licenseTables)).toBe(Object.prototype);
    expect(Object.prototype).not.toHaveProperty('page');
  });
});

describe('legacy URL normalization and query semantics', () => {
  it('binds markers and global pagination to the exact license and package', () => {
    const migrated = normalizeLicenseFindingsSearch(
      {
        marked: 'MIT',
        packageMarked: identifier,
        packagePage: 2,
        packagePageSize: 20,
        packageId: 'ignored',
        packageSortBy: [{ id: 'identifier', desc: true }],
        findingsPage: 4,
        findingsPageSize: 30,
        page: 3,
      },
      packageIdTypeSchema.enum.PURL
    );
    expect(migrated).toEqual({
      marked: 'MIT',
      page: 3,
      licenseTables: {
        MIT: {
          page: 2,
          pageSize: 20,
          packageMarked: identifier,
          sortBy: [{ id: 'identifier', desc: true }],
          packages: { [identifier]: { page: 4, pageSize: 30 } },
        },
      },
    });
    expect(
      getLicensePackagesQueryFilter(
        getLicensePackagesTableState(migrated, 'MIT')
      )
    ).toEqual({ identifier, identifierMatchType: 'exact' });
    expect(
      normalizeLicenseFindingsSearch(migrated, packageIdTypeSchema.enum.ORT_ID)
    ).toEqual(migrated);
  });

  it('gives explicit scoped entries precedence over all legacy defaults', () => {
    const migrated = normalizeLicenseFindingsSearch(
      {
        marked: 'MIT',
        packageMarked: identifier,
        packagePage: 8,
        findingsPage: 5,
        licenseTables: { MIT: {} },
      },
      packageIdTypeSchema.enum.ORT_ID
    );
    expect(migrated).toEqual({ marked: 'MIT', licenseTables: { MIT: {} } });
  });

  it('consumes unbound defaults once, within the first explicitly opened branch', () => {
    const legacy = {
      packagePage: 3,
      packageId: 'pkg:npm/example',
      findingsPage: 4,
    };
    expect(
      normalizeLicenseFindingsSearch(legacy, packageIdTypeSchema.enum.PURL)
    ).toEqual(legacy);
    const opened = toggleLicenseTable(
      legacy,
      'MIT',
      packageIdTypeSchema.enum.PURL
    );
    expect(opened).toEqual({
      licenseTables: {
        MIT: {
          page: 3,
          packageId: 'pkg:npm/example',
          packageIdType: packageIdTypeSchema.enum.PURL,
          legacyFindings: { page: 4 },
        },
      },
    });
    const sibling = toggleLicenseTable(
      opened,
      'Apache-2.0',
      packageIdTypeSchema.enum.PURL
    );
    const siblingPackage = toggleLicensePackageTable(
      sibling,
      'Apache-2.0',
      identifier
    );
    expect(
      getLicenseFindingsTableState(siblingPackage, 'Apache-2.0', identifier)
    ).toEqual({});
    const firstPackage = toggleLicensePackageTable(
      siblingPackage,
      'MIT',
      identifier
    );
    expect(
      getLicenseFindingsTableState(firstPackage, 'MIT', identifier)
    ).toEqual({ page: 4 });
    expect(
      getLicensePackagesTableState(firstPackage, 'MIT')
    ).not.toHaveProperty('legacyFindings');
    const secondPackage = toggleLicensePackageTable(
      firstPackage,
      'MIT',
      siblingIdentifier
    );
    expect(
      getLicenseFindingsTableState(secondPackage, 'MIT', siblingIdentifier)
    ).toEqual({});
    const closed = toggleLicenseTable(
      opened,
      'MIT',
      packageIdTypeSchema.enum.PURL
    );
    expect(
      toggleLicenseTable(closed, 'MIT', packageIdTypeSchema.enum.PURL)
    ).toEqual({
      licenseTables: { MIT: {} },
    });
  });

  it('keeps a legacy package marker until its missing license is explicitly opened', () => {
    const legacy = { packageMarked: identifier, findingsPage: 3 };
    expect(
      normalizeLicenseFindingsSearch(legacy, packageIdTypeSchema.enum.PURL)
    ).toEqual(legacy);
    const opened = toggleLicenseTable(
      legacy,
      'MIT',
      packageIdTypeSchema.enum.PURL
    );
    expect(opened).toEqual({
      licenseTables: {
        MIT: {
          packageMarked: identifier,
          packages: { [identifier]: { page: 3 } },
        },
      },
    });
  });

  it('clears exact focus only in the table being interacted with', () => {
    const search = makeSearch();
    search.marked = 'MIT';
    search.licenseTables = Object.fromEntries(
      Object.entries(search.licenseTables ?? {}).map(([license, table]) => [
        license,
        { ...table, packageMarked: identifier },
      ])
    );
    const paged = updateLicensePackagesTable(search, 'MIT', { page: 2 });
    expect(paged.marked).toBe('MIT');
    expect(paged.licenseTables?.MIT).not.toHaveProperty('packageMarked');
    expect(paged.licenseTables?.['Apache-2.0']?.packageMarked).toBe(identifier);
    const sorted = updateLicensePackagesTable(search, 'MIT', {
      sortBy: [{ id: 'purl', desc: true }],
    });
    expect(sorted.licenseTables?.MIT?.packageMarked).toBe(identifier);
    const toggled = toggleLicenseTable(
      search,
      'Apache-2.0',
      packageIdTypeSchema.enum.ORT_ID
    );
    expect(toggled).not.toHaveProperty('marked');
    expect(toggled.licenseTables?.MIT?.packageMarked).toBe(identifier);
  });

  it.each([
    [
      {
        packageId: 'pkg:maven/example',
        packageIdType: packageIdTypeSchema.enum.PURL,
      },
      { purl: 'pkg:maven/example' },
    ],
    [
      {
        packageId: 'Maven:example',
        packageIdType: packageIdTypeSchema.enum.ORT_ID,
      },
      { identifier: 'Maven:example' },
    ],
    [
      {
        packageMarked: identifier,
        packageIdType: packageIdTypeSchema.enum.PURL,
      },
      { identifier, identifierMatchType: 'exact' },
    ],
  ] satisfies [LicensePackagesTableState, object][])(
    'uses URL filter semantics for %j',
    (table, expected) => {
      expect(getLicensePackagesQueryFilter(table)).toEqual(expected);
    }
  );
});
