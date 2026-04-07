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

import { useQuery } from '@tanstack/react-query';
import { useNavigate, useSearch } from '@tanstack/react-router';
import {
  createColumnHelper,
  ExpandedState,
  getCoreRowModel,
  getExpandedRowModel,
  Row,
  useReactTable,
} from '@tanstack/react-table';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { useState } from 'react';

import { DetectedLicense, PackageIdentifier } from '@/api';
import { getRunPackagesWithDetectedLicenseOptions } from '@/api/@tanstack/react-query.gen';
import { BreakableString } from '@/components/breakable-string';
import { CopyToClipboard } from '@/components/copy-to-clipboard';
import { DataTable } from '@/components/data-table/data-table';
import { LoadingIndicator } from '@/components/loading-indicator';
import { Button } from '@/components/ui/button';
import {
  convertToBackendSorting,
  updateColumnSorting,
} from '@/helpers/handle-multisort';
import { identifierToString } from '@/helpers/identifier-conversion';
import { ACTION_COLUMN_SIZE } from '@/lib/constants';
import { toastError } from '@/lib/toast';
import { PackageIdType } from '@/schemas';
import { useUserSettingsStore } from '@/store/user-settings.store';
import { DetectedLicenseFindingsTable } from './detected-license-findings-table';

const packageColumnHelper = createColumnHelper<PackageIdentifier>();
const defaultPageSize = 10;
const licenseFindingsRoutePath =
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/license-findings/';

const PackageIdCell = ({
  pkg,
  packageIdType,
}: {
  pkg: PackageIdentifier;
  packageIdType: PackageIdType;
}) => {
  const id =
    packageIdType === 'PURL' && pkg.purl
      ? pkg.purl
      : identifierToString(pkg.identifier);

  return (
    <div className='flex items-center'>
      <BreakableString text={id} className='font-semibold' />
      <CopyToClipboard copyText={id} />
    </div>
  );
};

type DetectedLicensePackagesTableProps = {
  row: Row<DetectedLicense>;
  runId: number;
};

export const DetectedLicensePackagesTable = ({
  row,
  runId,
}: DetectedLicensePackagesTableProps) => {
  const search = useSearch({ from: licenseFindingsRoutePath });
  const navigate = useNavigate({ from: licenseFindingsRoutePath });
  const packageIdType = useUserSettingsStore((state) => state.packageIdType);
  const packagePageIndex = search.packagePage ? search.packagePage - 1 : 0;
  const packagePageSize = search.packagePageSize || defaultPageSize;
  const packageIdFilter = search.packageId;
  const packageSortBy = search.packageSortBy;
  const packageColumnId = packageIdType === 'PURL' ? 'purl' : 'identifier';
  const [packageExpanded, setPackageExpanded] = useState<ExpandedState>({});

  const {
    data: packages,
    isPending,
    isError,
    error,
  } = useQuery({
    ...getRunPackagesWithDetectedLicenseOptions({
      path: {
        runId,
        license: row.original.license,
      },
      query: {
        limit: packagePageSize,
        offset: packagePageIndex * packagePageSize,
        sort: convertToBackendSorting(packageSortBy),
        ...(packageIdType === 'PURL'
          ? { purl: packageIdFilter }
          : { identifier: packageIdFilter }),
      },
    }),
  });

  const packageColumns = [
    packageColumnHelper.display({
      id: 'details',
      header: 'Details',
      size: ACTION_COLUMN_SIZE,
      cell: function CellComponent({ row: packageRow }) {
        return packageRow.getCanExpand() ? (
          <Button
            variant='outline'
            size='sm'
            onClick={() => {
              const isOpening = !packageRow.getIsExpanded();

              packageRow.toggleExpanded();

              if (isOpening) {
                navigate({
                  search: {
                    ...search,
                    findingsPage: 1,
                  },
                  replace: true,
                });
              }
            }}
            style={{ cursor: 'pointer' }}
          >
            {packageRow.getIsExpanded() ? (
              <ChevronUp className='h-4 w-4' />
            ) : (
              <ChevronDown className='h-4 w-4' />
            )}
          </Button>
        ) : null;
      },
    }),
    packageColumnHelper.accessor(
      (pkg) =>
        packageIdType === 'PURL' && pkg.purl
          ? pkg.purl
          : identifierToString(pkg.identifier),
      {
        id: packageColumnId,
        header: packageIdType === 'PURL' ? 'PURL' : 'ORT ID',
        cell: ({ row }) => (
          <PackageIdCell pkg={row.original} packageIdType={packageIdType} />
        ),
        meta: {
          filter: {
            filterVariant: 'text',
            setFilterValue: (value: string | undefined) => {
              navigate({
                search: {
                  ...search,
                  packagePage: 1,
                  packageId: value,
                },
              });
            },
          },
        },
      }
    ),
  ];

  const packageTable = useReactTable({
    data: packages?.data || [],
    columns: packageColumns,
    pageCount: Math.ceil(
      (packages?.pagination.totalCount ?? 0) / packagePageSize
    ),
    state: {
      pagination: {
        pageIndex: packagePageIndex,
        pageSize: packagePageSize,
      },
      sorting: packageSortBy,
      expanded: packageExpanded,
      columnFilters: [{ id: packageColumnId, value: packageIdFilter }],
    },
    onExpandedChange: setPackageExpanded,
    getCoreRowModel: getCoreRowModel(),
    getExpandedRowModel: getExpandedRowModel(),
    getRowCanExpand: () => true,
    manualPagination: true,
  });

  if (isPending) {
    return <LoadingIndicator />;
  }

  if (isError) {
    toastError('Unable to load data', error);
    return <></>;
  }

  const filtersInUse =
    row.original.packageCount !== packages.pagination.totalCount;
  const matching = `, ${packages.pagination.totalCount} matching filters`;

  return (
    <div className='space-y-4 p-2'>
      <div className='text-muted-foreground text-sm'>
        Packages with this detected license ({row.original.packageCount} in
        total
        {filtersInUse && matching}).
      </div>
      <DataTable
        table={packageTable}
        className='[&_tbody_tr:first-child]:border-t'
        renderSubComponent={({ row: packageRow }) => (
          <DetectedLicenseFindingsTable
            runId={runId}
            license={row.original.license}
            identifier={identifierToString(packageRow.original.identifier)}
          />
        )}
        setCurrentPageOptions={(currentPage) => {
          return {
            to: '.',
            search: {
              ...search,
              packagePage: currentPage,
            },
          };
        }}
        setPageSizeOptions={(size) => {
          return {
            to: '.',
            search: {
              ...search,
              packagePage: 1,
              packagePageSize: size,
            },
          };
        }}
        setSortingOptions={(sortBy) => {
          return {
            to: '.',
            search: {
              ...search,
              packageSortBy: updateColumnSorting(search.packageSortBy, sortBy),
            },
          };
        }}
      />
    </div>
  );
};
