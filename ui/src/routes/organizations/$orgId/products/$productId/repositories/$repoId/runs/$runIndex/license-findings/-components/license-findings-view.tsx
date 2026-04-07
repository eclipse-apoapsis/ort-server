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
import {
  createColumnHelper,
  ExpandedState,
  getCoreRowModel,
  getExpandedRowModel,
  Row,
  useReactTable,
} from '@tanstack/react-table';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

import { DetectedLicense } from '@/api';
import {
  getRepositoryRunOptions,
  getRunDetectedLicensesOptions,
} from '@/api/@tanstack/react-query.gen';
import { DataTable } from '@/components/data-table/data-table';
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
  updateColumnSorting,
} from '@/helpers/handle-multisort';
import { toastError } from '@/lib/toast';
import { useUserSettingsStore } from '@/store/user-settings.store';
import { DetectedLicensePackagesTable } from './detected-license-packages-table';

const licenseColumnHelper = createColumnHelper<DetectedLicense>();
const defaultPageSize = 10;
const licenseFindingsRoutePath =
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/license-findings/';

const renderSubComponent = ({
  row,
  runId,
}: {
  row: Row<DetectedLicense>;
  runId: number;
}) => <DetectedLicensePackagesTable row={row} runId={runId} />;

export const LicenseFindingsView = () => {
  const params = useParams({ from: licenseFindingsRoutePath });
  const search = useSearch({ from: licenseFindingsRoutePath });
  const navigate = useNavigate({ from: licenseFindingsRoutePath });
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : defaultPageSize;
  const detectedLicenses = search.detectedLicense;
  const detectedLicenseFilter =
    detectedLicenses && detectedLicenses.length > 0
      ? detectedLicenses.join(',')
      : undefined;
  const packageIdType = useUserSettingsStore((state) => state.packageIdType);
  const previousPackageIdType = useRef(packageIdType);

  const { data: ortRun } = useSuspenseQuery({
    ...getRepositoryRunOptions({
      path: {
        repositoryId: Number.parseInt(params.repoId),
        ortRunIndex: Number.parseInt(params.runIndex),
      },
    }),
  });

  const { data: totalDetectedLicenses } = useSuspenseQuery({
    ...getRunDetectedLicensesOptions({
      path: { runId: ortRun.id },
      query: { limit: 1 },
    }),
  });

  const { data: detectedLicensesOptions } = useSuspenseQuery({
    ...getRunDetectedLicensesOptions({
      path: { runId: ortRun.id },
      query: {
        limit: totalDetectedLicenses.pagination.totalCount || 1,
      },
    }),
  });

  const {
    data: detectedLicenseFindings,
    isPending,
    isError,
    error,
  } = useSuspenseQuery({
    ...getRunDetectedLicensesOptions({
      path: { runId: ortRun.id },
      query: {
        limit: pageSize,
        offset: pageIndex * pageSize,
        sort: convertToBackendSorting(search.sortBy),
        license: detectedLicenseFilter,
      },
    }),
  });

  const columns = [
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
            onClick={() => {
              const isOpening = !row.getIsExpanded();

              setExpanded(isOpening ? { [row.id]: true } : {});
              navigate({
                search: {
                  ...search,
                  packagePage: 1,
                  packageId: undefined,
                  packageSortBy: undefined,
                  findingsPage: 1,
                },
                replace: true,
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
          filterVariant: 'select',
          align: 'end',
          selectOptions: detectedLicensesOptions.data.map((license) => ({
            label: license.license,
            value: license.license,
          })),
          setSelected: (licenses: string[]) => {
            navigate({
              search: {
                ...search,
                page: 1,
                detectedLicense: licenses.length === 0 ? undefined : licenses,
              },
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
  ];

  const [expanded, setExpanded] = useState<ExpandedState>({});

  const table = useReactTable({
    data: detectedLicenseFindings?.data || [],
    columns,
    pageCount: Math.ceil(
      (detectedLicenseFindings?.pagination.totalCount ?? 0) / pageSize
    ),
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
      sorting: search.sortBy,
      expanded,
      columnFilters: [{ id: 'license', value: detectedLicenses }],
    },
    getCoreRowModel: getCoreRowModel(),
    getExpandedRowModel: getExpandedRowModel(),
    getRowCanExpand: () => true,
    manualPagination: true,
  });

  useEffect(() => {
    if (previousPackageIdType.current === packageIdType) {
      return;
    }

    previousPackageIdType.current = packageIdType;
    navigate({
      search: {
        ...search,
        packagePage: 1,
        packageId: undefined,
        packageSortBy: undefined,
      },
      replace: true,
    });
    // Identifier mode changes invalidate the nested package query inputs.
  }, [navigate, packageIdType, search]);

  if (isPending) {
    return <LoadingIndicator />;
  }

  if (isError) {
    toastError('Unable to load data', error);
    return;
  }

  const filtersInUse =
    totalDetectedLicenses.pagination.totalCount !==
    detectedLicenseFindings.pagination.totalCount;
  const matching = `, ${detectedLicenseFindings.pagination.totalCount} matching filters`;

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
          renderSubComponent={({ row }) =>
            renderSubComponent({ row, runId: ortRun.id })
          }
          setCurrentPageOptions={(currentPage) => {
            return {
              to: '.',
              search: { ...search, page: currentPage },
            };
          }}
          setPageSizeOptions={(size) => {
            return {
              to: '.',
              search: { ...search, page: 1, pageSize: size },
            };
          }}
          setSortingOptions={(sortBy) => {
            return {
              to: '.',
              search: {
                ...search,
                sortBy: updateColumnSorting(search.sortBy, sortBy),
              },
            };
          }}
        />
      </CardContent>
    </Card>
  );
};
