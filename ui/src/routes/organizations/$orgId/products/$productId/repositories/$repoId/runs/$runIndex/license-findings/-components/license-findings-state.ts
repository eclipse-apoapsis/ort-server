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

import { ExpandedState } from '@tanstack/react-table';

import { DEFAULT_PAGE_SIZE } from '@/components/data-table/data-table';
import {
  licensePackagesTableStateSchema,
  licenseTablesSearchParameterSchema,
  packageIdTypeSchema,
  type LicenseFindingsSearchParameters,
  type LicenseFindingsTableState,
  type LicensePackagesTableState,
  type PackageIdType,
} from '@/schemas';

export const getDetectedLicenseQueryFilter = (
  marked: string | undefined,
  license: string | undefined
) => (marked ? { license: marked } : { license });

export const getPackageIdentifierQueryFilter = (
  packageMarked: string | undefined,
  packageIdType: PackageIdType,
  packageId: string | undefined
) => {
  if (packageMarked) {
    return {
      identifier: packageMarked,
      identifierMatchType: 'exact',
    };
  }

  return packageIdType === packageIdTypeSchema.enum.PURL
    ? { purl: packageId }
    : { identifier: packageId };
};

export const clearDetectedLicenseMarkers = <T extends object>(search: T) => ({
  ...search,
  marked: undefined,
  packageMarked: undefined,
});

type LicenseTables = NonNullable<
  LicenseFindingsSearchParameters['licenseTables']
>;

/** Read only own keys so identifiers such as "constructor" never resolve inherited values. */
const ownEntry = <T>(entries: Record<string, T> | undefined, key: string) =>
  entries && Object.hasOwn(entries, key) ? entries[key] : undefined;

/** Omit default pagination values from an already validated table state. */
const compactPagination = (
  state: LicenseFindingsTableState
): LicenseFindingsTableState => ({
  ...(state.page && state.page !== 1 ? { page: state.page } : {}),
  ...(state.pageSize && state.pageSize !== DEFAULT_PAGE_SIZE
    ? { pageSize: state.pageSize }
    : {}),
});

/** Prune default controls and prefer exact package focus, preserving open descendant entries. */
const compactPackageTable = (
  state: LicensePackagesTableState
): LicensePackagesTableState => {
  const pagination = compactPagination(state);
  const legacyFindings =
    state.legacyFindings && compactPagination(state.legacyFindings);
  return {
    ...pagination,
    ...(state.packageMarked
      ? { packageMarked: state.packageMarked }
      : state.packageId
        ? {
            packageId: state.packageId,
            packageIdType:
              state.packageIdType ?? packageIdTypeSchema.enum.ORT_ID,
          }
        : {}),
    ...(state.sortBy?.length ? { sortBy: state.sortBy } : {}),
    ...(state.packages && Object.keys(state.packages).length
      ? {
          packages: Object.fromEntries(
            Object.entries(state.packages).map(([id, table]) => [
              id,
              compactPagination(table),
            ])
          ),
        }
      : {}),
    ...(legacyFindings && Object.keys(legacyFindings).length
      ? { legacyFindings }
      : {}),
  };
};

/** Replace the hierarchy without mutating search, removing its URL parameter when empty. */
const withLicenseTables = (
  search: LicenseFindingsSearchParameters,
  tables: LicenseTables | undefined
): LicenseFindingsSearchParameters => {
  const result = { ...search };
  if (tables && Object.keys(tables).length) result.licenseTables = tables;
  else delete result.licenseTables;
  return result;
};

/** Remove default controls, but keep empty entries: their presence means expanded. */
export const canonicalizeLicenseTables = (
  search: LicenseFindingsSearchParameters
) => {
  const { licenseTables } = licenseTablesSearchParameterSchema.parse(search);
  return withLicenseTables(
    search,
    licenseTables &&
      Object.fromEntries(
        Object.entries(licenseTables).map(([license, table]) => [
          license,
          compactPackageTable(table),
        ])
      )
  );
};

/** Read one license's package controls, returning default controls when it is closed. */
export const getLicensePackagesTableState = (
  search: LicenseFindingsSearchParameters,
  license: string
): LicensePackagesTableState => ownEntry(search.licenseTables, license) ?? {};

/** Read findings controls by both license and package, returning defaults for absent entries. */
export const getLicenseFindingsTableState = (
  search: LicenseFindingsSearchParameters,
  license: string,
  identifier: string
): LicenseFindingsTableState =>
  ownEntry(
    getLicensePackagesTableState(search, license).packages,
    identifier
  ) ?? {};

/** Apply pagination defaults and convert the URL's one-based page to a table index. */
export const getLicenseTablePagination = (
  state: LicenseFindingsTableState
) => ({
  pageIndex: (state.page ?? 1) - 1,
  pageSize: state.pageSize ?? DEFAULT_PAGE_SIZE,
});

/** Convert license entry presence into TanStack Table's expanded-row map. */
export const getLicenseTablesExpandedState = (
  search: LicenseFindingsSearchParameters
): ExpandedState =>
  Object.fromEntries(
    Object.keys(search.licenseTables ?? {}).map((license) => [license, true])
  );

/** Derive expanded package rows within one license, independently of other licenses. */
export const getLicensePackagesExpandedState = (
  search: LicenseFindingsSearchParameters,
  license: string
): ExpandedState =>
  Object.fromEntries(
    Object.keys(
      getLicensePackagesTableState(search, license).packages ?? {}
    ).map((id) => [id, true])
  );

/** The URL's filter type, not the viewer's display preference, determines its meaning. */
export const getLicensePackagesQueryFilter = (
  state: LicensePackagesTableState
) =>
  getPackageIdentifierQueryFilter(
    state.packageMarked,
    state.packageIdType ?? packageIdTypeSchema.enum.ORT_ID,
    state.packageId
  );

/** Validate and compact one license's package table while preserving sibling license entries. */
const replacePackageTable = (
  search: LicenseFindingsSearchParameters,
  license: string,
  table: LicensePackagesTableState
) =>
  withLicenseTables(search, {
    ...search.licenseTables,
    [license]: compactPackageTable(
      licensePackagesTableStateSchema.parse(table)
    ),
  });

/** Remove consumed global nested-table controls while retaining top-level controls and focus. */
const clearLegacyTableControls = (search: LicenseFindingsSearchParameters) => {
  const result = { ...search };
  delete result.packagePage;
  delete result.packagePageSize;
  delete result.packageId;
  delete result.packageSortBy;
  delete result.packageMarked;
  delete result.findingsPage;
  delete result.findingsPageSize;
  return result;
};

/**
 * Bind old global controls to a marked license, or to the first explicitly opened
 * license. Unbound findings defaults remain within that license until a package opens.
 * Existing scoped entries win, and repeated normalization is idempotent.
 */
export const normalizeLicenseFindingsSearch = (
  search: LicenseFindingsSearchParameters,
  packageIdType: PackageIdType,
  openedLicense?: string
): LicenseFindingsSearchParameters => {
  const normalized = canonicalizeLicenseTables(search);
  const license = search.marked || openedLicense;
  if (!license) return normalized;
  const cleared = clearLegacyTableControls(normalized);
  if (ownEntry(normalized.licenseTables, license)) return cleared;

  const findings = {
    page: search.findingsPage,
    pageSize: search.findingsPageSize,
  };
  return replacePackageTable(cleared, license, {
    page: search.packagePage,
    pageSize: search.packagePageSize,
    packageId: search.packageId,
    packageIdType,
    sortBy: licensePackagesTableStateSchema.shape.sortBy.parse(
      search.packageSortBy
    ),
    packageMarked: search.packageMarked,
    ...(search.packageMarked
      ? { packages: { [search.packageMarked]: findings } }
      : { legacyFindings: findings }),
  });
};

/** Reset identifier-dependent package controls once when the global display preference changes. */
export const resetLicensePackageIdentifierControls = (
  search: LicenseFindingsSearchParameters
): LicenseFindingsSearchParameters =>
  withLicenseTables(
    search,
    search.licenseTables &&
      Object.fromEntries(
        Object.entries(search.licenseTables).map(([license, table]) => {
          const updated = { ...table };
          delete updated.page;
          delete updated.packageId;
          delete updated.packageIdType;
          delete updated.packageMarked;
          delete updated.sortBy;
          return [license, updated];
        })
      )
  );

/** Closing discards the entire selected subtree; opening never restores closed state. */
export const toggleLicenseTable = (
  search: LicenseFindingsSearchParameters,
  license: string,
  packageIdType: PackageIdType
): LicenseFindingsSearchParameters => {
  const normalized = normalizeLicenseFindingsSearch(search, packageIdType);
  const result = { ...normalized };
  delete result.marked;
  if (ownEntry(normalized.licenseTables, license)) {
    const tables = { ...normalized.licenseTables };
    delete tables[license];
    return withLicenseTables(result, tables);
  }
  return normalizeLicenseFindingsSearch(result, packageIdType, license);
};

/** Toggle one open license's package, discarding closed findings or consuming legacy defaults on open. */
export const toggleLicensePackageTable = (
  search: LicenseFindingsSearchParameters,
  license: string,
  identifier: string
): LicenseFindingsSearchParameters => {
  const table = ownEntry(search.licenseTables, license);
  if (!table) return search;
  const packages = { ...table.packages };
  if (ownEntry(packages, identifier)) delete packages[identifier];
  else
    Object.defineProperty(packages, identifier, {
      value: table.legacyFindings ?? {},
      enumerable: true,
      configurable: true,
      writable: true,
    });
  return replacePackageTable(search, license, {
    ...table,
    packages,
    packageMarked: undefined,
    legacyFindings: undefined,
  });
};

export type LicensePackagesTableChange =
  | { page: number }
  | { pageSize: number }
  | { packageId: string | undefined; packageIdType: PackageIdType }
  | { sortBy: LicensePackagesTableState['sortBy'] };

/** Update only an open license's package controls, leaving all findings tables intact. */
export const updateLicensePackagesTable = (
  search: LicenseFindingsSearchParameters,
  license: string,
  change: LicensePackagesTableChange
): LicenseFindingsSearchParameters => {
  const table = ownEntry(search.licenseTables, license);
  if (!table) return search;
  return replacePackageTable(search, license, {
    ...table,
    ...change,
    page: 'page' in change ? change.page : 1,
    packageMarked: 'sortBy' in change ? table.packageMarked : undefined,
  });
};

/** Update only open findings, resetting their page when page size changes without reopening closed rows. */
export const updateLicenseFindingsTable = (
  search: LicenseFindingsSearchParameters,
  license: string,
  identifier: string,
  change: { page: number } | { pageSize: number }
): LicenseFindingsSearchParameters => {
  const table = ownEntry(search.licenseTables, license);
  const findings = ownEntry(table?.packages, identifier);
  if (!table || !findings) return search;
  return replacePackageTable(search, license, {
    ...table,
    packages: {
      ...table.packages,
      [identifier]: {
        ...findings,
        ...change,
        page: 'page' in change ? change.page : 1,
      },
    },
  });
};
