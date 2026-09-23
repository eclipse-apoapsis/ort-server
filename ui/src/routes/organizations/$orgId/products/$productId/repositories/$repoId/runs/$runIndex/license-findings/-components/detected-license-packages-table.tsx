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
import {
  Link,
  useNavigate,
  useParams,
  useSearch,
} from '@tanstack/react-router';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { useEffect, useRef } from 'react';

import { DetectedLicense, PackageIdentifier } from '@/api';
import { getRunPackagesWithDetectedLicenseOptions } from '@/api/@tanstack/react-query.gen';
import { BreakableString } from '@/components/breakable-string';
import { CopyToClipboard } from '@/components/copy-to-clipboard';
import { DataTable } from '@/components/data-table/data-table';
import { LoadingIndicator } from '@/components/loading-indicator';
import { Button } from '@/components/ui/button';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import {
  convertToBackendSorting,
  EMPTY_SORTING_STATE,
  updateColumnSorting,
} from '@/helpers/handle-multisort';
import { identifierToString } from '@/helpers/identifier-conversion';
import {
  createAppColumnHelper,
  selectNoTableState,
  useAppTable,
} from '@/hooks/use-app-table';
import type { AppRow } from '@/hooks/use-app-table';
import { ACTION_COLUMN_SIZE } from '@/lib/constants';
import {
  KEEP_SCROLL_POSITION,
  markPanelOpened,
  panelKey,
  scrollOpenedPanelIntoView,
} from '@/lib/scroll';
import { toastError } from '@/lib/toast';
import {
  licensePackagesTableStateSchema,
  packageIdTypeSchema,
  type PackageIdType,
} from '@/schemas';
import { useUserSettingsStore } from '@/store/user-settings.store';
import { DetectedLicenseFindingsTable } from './detected-license-findings-table';
import {
  getLicensePackagesExpandedState,
  getLicensePackagesQueryFilter,
  getLicensePackagesTableState,
  getLicenseTablePagination,
  toggleLicensePackageTable,
  updateLicensePackagesTable,
} from './license-findings-state';

const packageColumnHelper = createAppColumnHelper<PackageIdentifier>();
const licenseFindingsRoutePath =
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/license-findings/';

const PackageIdCell = ({
  pkg,
  packageIdType,
}: {
  pkg: PackageIdentifier;
  packageIdType: PackageIdType;
}) => {
  const params = useParams({ from: licenseFindingsRoutePath });
  const purl =
    packageIdType === packageIdTypeSchema.enum.PURL && pkg.purl
      ? pkg.purl
      : undefined;
  const id = purl ?? identifierToString(pkg.identifier);
  const idType = purl
    ? packageIdTypeSchema.enum.PURL
    : packageIdTypeSchema.enum.ORT_ID;

  return (
    <div className='flex items-center'>
      <Tooltip>
        <TooltipTrigger asChild>
          <Link
            className='text-left font-semibold text-blue-400 hover:underline'
            to='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/packages'
            params={params}
            search={{ pkgId: id, pkgIdType: idType, marked: '0' }}
          >
            <BreakableString text={id} />
          </Link>
        </TooltipTrigger>
        <TooltipContent>
          Inspect the package details in packages table
        </TooltipContent>
      </Tooltip>
      <CopyToClipboard copyText={id} />
    </div>
  );
};

type DetectedLicensePackagesTableProps = {
  row: AppRow<DetectedLicense>;
  runId: number;
};

export const DetectedLicensePackagesTable = ({
  row,
  runId,
}: DetectedLicensePackagesTableProps) => {
  const search = useSearch({ from: licenseFindingsRoutePath });
  const navigate = useNavigate({ from: licenseFindingsRoutePath });
  const packageIdType = useUserSettingsStore((state) => state.packageIdType);
  const license = row.original.license;
  const tableState = getLicensePackagesTableState(search, license);
  const { pageIndex: packagePageIndex, pageSize: packagePageSize } =
    getLicenseTablePagination(tableState);
  const packageIdFilter = tableState.packageId;
  const packageSortBy = tableState.sortBy;
  const packageColumnId =
    packageIdType === packageIdTypeSchema.enum.PURL ? 'purl' : 'identifier';
  const panel = useRef<HTMLElement>(null);

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
        ...getLicensePackagesQueryFilter(tableState),
      },
    }),
  });

  const packageColumns = packageColumnHelper.columns([
    packageColumnHelper.display({
      id: 'details',
      header: 'Details',
      size: ACTION_COLUMN_SIZE,
      cell: function CellComponent({ row: packageRow }) {
        return packageRow.getCanExpand() ? (
          <Button
            variant='outline'
            size='sm'
            aria-label={`License findings for ${packageRow.id} under ${license}`}
            aria-expanded={packageRow.getIsExpanded()}
            onClick={() => {
              if (!packageRow.getIsExpanded()) {
                markPanelOpened(panelKey(license, packageRow.id));
              }
              navigate({
                search: (previous) =>
                  toggleLicensePackageTable(previous, license, packageRow.id),
                ...KEEP_SCROLL_POSITION,
              });
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
        packageIdType === packageIdTypeSchema.enum.PURL && pkg.purl
          ? pkg.purl
          : identifierToString(pkg.identifier),
      {
        id: packageColumnId,
        header:
          packageIdType === packageIdTypeSchema.enum.PURL ? 'PURL' : 'ORT ID',
        cell: ({ row }) => (
          <PackageIdCell pkg={row.original} packageIdType={packageIdType} />
        ),
        meta: {
          filter: {
            filterVariant: 'text',
            setFilterValue: (value: string | undefined) => {
              navigate({
                search: (previous) =>
                  updateLicensePackagesTable(previous, license, {
                    packageId: value,
                    packageIdType,
                  }),
                ...KEEP_SCROLL_POSITION,
              });
            },
          },
        },
      }
    ),
  ]);

  const packageTable = useAppTable(
    {
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
        sorting: packageSortBy ?? EMPTY_SORTING_STATE,
        expanded: getLicensePackagesExpandedState(search, license),
        columnFilters: [{ id: packageColumnId, value: packageIdFilter }],
      },
      getRowCanExpand: () => true,
      getRowId: (row) => identifierToString(row.identifier),
      manualPagination: true,
    },
    selectNoTableState
  );

  useEffect(() => {
    scrollOpenedPanelIntoView(panelKey(license), panel.current);
  }, [license, packages]);

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
    <section
      ref={panel}
      aria-label={`Packages for ${license}`}
      className='space-y-4 p-2'
    >
      <div className='text-muted-foreground text-sm'>
        Packages with this detected license ({row.original.packageCount} in
        total
        {filtersInUse && matching}).
      </div>
      {packageIdFilter && tableState.packageIdType !== packageIdType && (
        <div className='text-muted-foreground text-sm'>
          Filtering by{' '}
          {tableState.packageIdType === packageIdTypeSchema.enum.PURL
            ? 'PURL'
            : 'ORT ID'}
          : {packageIdFilter}
        </div>
      )}
      <DataTable
        table={packageTable}
        className='[&_tbody_tr:first-child]:border-t'
        renderSubComponent={({ row: packageRow }) => (
          <DetectedLicenseFindingsTable
            runId={runId}
            license={row.original.license}
            identifier={identifierToString(packageRow.original.identifier)}
            purl={packageRow.original.purl ?? undefined}
          />
        )}
        setCurrentPageOptions={(currentPage) => {
          return {
            to: '.',
            search: (previous) =>
              updateLicensePackagesTable(previous, license, {
                page: currentPage,
              }),
            ...KEEP_SCROLL_POSITION,
          };
        }}
        setPageSizeOptions={(size) => {
          return {
            to: '.',
            search: (previous) =>
              updateLicensePackagesTable(previous, license, { pageSize: size }),
            ...KEEP_SCROLL_POSITION,
          };
        }}
        setSortingOptions={(sortBy) => {
          return {
            to: '.',
            search: (previous) =>
              updateLicensePackagesTable(previous, license, {
                sortBy: licensePackagesTableStateSchema.shape.sortBy.parse(
                  updateColumnSorting(
                    getLicensePackagesTableState(previous, license).sortBy,
                    sortBy
                  )
                ),
              }),
            ...KEEP_SCROLL_POSITION,
          };
        }}
      />
    </section>
  );
};
