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

import { useSuspenseQuery } from '@tanstack/react-query';
import { useNavigate, useParams, useSearch } from '@tanstack/react-router';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

import { DetectedLicense } from '@/api';
import {
  getRepositoryRunOptions,
  getRunDetectedLicensesInfiniteOptions,
  getRunDetectedLicensesOptions,
} from '@/api/@tanstack/react-query.gen';
import {
  DataTable,
  DEFAULT_PAGE_SIZE,
} from '@/components/data-table/data-table';
import { SpdxExpressionBadgeGroup } from '@/components/licenses';
import { LoadingIndicator } from '@/components/loading-indicator';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {
  convertToBackendSorting,
  EMPTY_SORTING_STATE,
  updateColumnSorting,
} from '@/helpers/handle-multisort';
import {
  createAppColumnHelper,
  selectNoTableState,
  useAppTable,
} from '@/hooks/use-app-table';
import type { AppRow } from '@/hooks/use-app-table';
import { useDebounce } from '@/hooks/use-debounce';
import { useInfiniteList } from '@/hooks/use-infinite-list';
import { DROPDOWN_PAGE_SIZE } from '@/lib/constants';
import { toastError } from '@/lib/toast';
import { useUserSettingsStore } from '@/store/user-settings.store';
import { DetectedLicensePackagesTable } from './detected-license-packages-table';
import {
  clearDetectedLicenseMarkers,
  getDetectedLicenseQueryFilter,
  getLicenseTablesExpandedState,
  normalizeLicenseFindingsSearch,
  resetLicensePackageIdentifierControls,
  toggleLicenseTable,
} from './license-findings-state';

const licenseColumnHelper = createAppColumnHelper<DetectedLicense>();
const licenseFindingsRoutePath =
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/license-findings/';

const renderSubComponent = ({
  row,
  runId,
}: {
  row: AppRow<DetectedLicense>;
  runId: number;
}) => <DetectedLicensePackagesTable row={row} runId={runId} />;

export const LicenseFindingsView = () => {
  const params = useParams({ from: licenseFindingsRoutePath });
  const search = useSearch({ from: licenseFindingsRoutePath });
  const navigate = useNavigate({ from: licenseFindingsRoutePath });
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ?? DEFAULT_PAGE_SIZE;
  const detectedLicenses = search.detectedLicense;
  const detectedLicenseFilter =
    detectedLicenses && detectedLicenses.length > 0
      ? detectedLicenses.join(',')
      : undefined;
  const packageIdType = useUserSettingsStore((state) => state.packageIdType);
  const previousPackageIdType = useRef(packageIdType);
  const normalizedSearch = normalizeLicenseFindingsSearch(
    search,
    packageIdType
  );
  const needsNormalization =
    JSON.stringify(normalizedSearch) !== JSON.stringify(search);
  const [licenseFilterOpen, setLicenseFilterOpen] = useState(false);
  const [licenseSearchTerm, setLicenseSearchTerm] = useState('');
  const debouncedLicenseSearchTerm = useDebounce(licenseSearchTerm, 300).trim();

  const { data: ortRun } = useSuspenseQuery({
    ...getRepositoryRunOptions({
      path: {
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      },
    }),
  });

  const detectedLicenseOptions = useInfiniteList(
    getRunDetectedLicensesInfiniteOptions({
      path: { runId: ortRun.id },
      query: {
        limit: DROPDOWN_PAGE_SIZE,
        licenseSearch: debouncedLicenseSearchTerm || undefined,
      },
    }),
    { enabled: licenseFilterOpen }
  );
  const licenseFilterOptions = {
    ...detectedLicenseOptions,
    items: detectedLicenseOptions.items.map(({ license }) => ({
      label: license,
      value: license,
    })),
  };

  const { data: totalDetectedLicenses } = useSuspenseQuery({
    ...getRunDetectedLicensesOptions({
      path: { runId: ortRun.id },
      query: { limit: 1 },
    }),
  });

  const {
    data: detectedLicenseFindings,
    isError,
    error,
  } = useSuspenseQuery({
    ...getRunDetectedLicensesOptions({
      path: { runId: ortRun.id },
      query: {
        limit: pageSize,
        offset: pageIndex * pageSize,
        sort: convertToBackendSorting(search.sortBy),
        ...getDetectedLicenseQueryFilter(search.marked, detectedLicenseFilter),
      },
    }),
  });

  const columns = licenseColumnHelper.columns([
    licenseColumnHelper.display({
      id: 'details',
      header: 'Details',
      meta: {
        widthPercentage: 8,
      },
      cell: function CellComponent({ row }) {
        return row.getCanExpand() ? (
          <Button
            variant='outline'
            size='sm'
            aria-label={`Packages for ${row.original.license}`}
            aria-expanded={row.getIsExpanded()}
            onClick={() => {
              navigate({
                search: (previous) =>
                  toggleLicenseTable(previous, row.id, packageIdType),
              });
            }}
            style={{ cursor: 'pointer' }}
          >
            {row.getIsExpanded() ? (
              <ChevronUp className='h-4 w-4' />
            ) : (
              <ChevronDown className='h-4 w-4' />
            )}
          </Button>
        ) : null;
      },
    }),
    licenseColumnHelper.accessor('license', {
      id: 'license',
      header: 'Detected License',
      cell: ({ row }) => (
        <SpdxExpressionBadgeGroup expression={row.original.license} />
      ),
      meta: {
        isGrow: true,
        filter: {
          filterVariant: 'infinite-select',
          align: 'end',
          selectOptions: licenseFilterOptions,
          getSelectedOption: (license: string) => ({
            label: license,
            value: license,
          }),
          open: licenseFilterOpen,
          onOpenChange: setLicenseFilterOpen,
          searchTerm: licenseSearchTerm,
          onSearchTermChange: setLicenseSearchTerm,
          setSelected: (licenses: string[]) => {
            navigate({
              search: (previous) =>
                clearDetectedLicenseMarkers({
                  ...previous,
                  page: 1,
                  detectedLicense: licenses.length === 0 ? undefined : licenses,
                }),
            });
          },
        },
      },
    }),
    licenseColumnHelper.accessor('packageCount', {
      id: 'packageCount',
      header: 'Package Count',
      meta: {
        widthPercentage: 14,
      },
    }),
  ]);

  const table = useAppTable(
    {
      data: detectedLicenseFindings.data,
      columns,
      pageCount: Math.ceil(
        detectedLicenseFindings.pagination.totalCount / pageSize
      ),
      state: {
        pagination: {
          pageIndex,
          pageSize,
        },
        sorting: search.sortBy ?? EMPTY_SORTING_STATE,
        expanded: getLicenseTablesExpandedState(search),
        columnFilters: [{ id: 'license', value: detectedLicenses }],
      },
      getRowCanExpand: () => true,
      getRowId: (row) => row.license,
      manualPagination: true,
    },
    selectNoTableState
  );

  useEffect(() => {
    const identifierTypeChanged =
      previousPackageIdType.current !== packageIdType;
    previousPackageIdType.current = packageIdType;
    if (!needsNormalization && !identifierTypeChanged) return;

    navigate({
      search: (previous) => {
        const normalized = normalizeLicenseFindingsSearch(
          previous,
          packageIdType
        );
        return identifierTypeChanged
          ? resetLicensePackageIdentifierControls(normalized)
          : normalized;
      },
      replace: true,
    });
  }, [navigate, packageIdType, needsNormalization, search]);

  // Resolve old links before mounting nested tables with their scoped query inputs.
  if (needsNormalization) return <LoadingIndicator />;

  if (isError) {
    toastError('Unable to load data', error);
    return;
  }

  const filtersInUse =
    totalDetectedLicenses.pagination.totalCount !==
    detectedLicenseFindings.pagination.totalCount;
  const matching = `, ${detectedLicenseFindings.pagination.totalCount} matching filters`;
  const scannerWasIncludedInRun = ortRun.jobs.scanner != null;
  const noResultsContent = !scannerWasIncludedInRun ? (
    <div className='text-muted-foreground text-sm'>
      No detected licenses are available because the scanner job was not enabled
      for this run.
    </div>
  ) : undefined;

  return (
    <Card className='h-fit'>
      <CardHeader>
        <CardTitle>
          Detected Licenses ({totalDetectedLicenses.pagination.totalCount} in
          total
          {filtersInUse && matching})
        </CardTitle>
        <CardDescription>
          Licenses as detected in projects and packages by the configured
          scanners.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <DataTable
          table={table}
          noResultsContent={noResultsContent}
          renderSubComponent={({ row }) =>
            renderSubComponent({ row, runId: ortRun.id })
          }
          setCurrentPageOptions={(currentPage) => {
            return {
              to: '.',
              search: (previous) =>
                clearDetectedLicenseMarkers({
                  ...previous,
                  page: currentPage,
                }),
            };
          }}
          setPageSizeOptions={(size) => {
            return {
              to: '.',
              search: (previous) =>
                clearDetectedLicenseMarkers({
                  ...previous,
                  page: 1,
                  pageSize: size,
                }),
            };
          }}
          setSortingOptions={(sortBy) => {
            return {
              to: '.',
              search: (previous) => ({
                ...previous,
                page: 1,
                sortBy: updateColumnSorting(previous.sortBy, sortBy),
              }),
            };
          }}
        />
      </CardContent>
    </Card>
  );
};
