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
import { useSearch } from '@tanstack/react-router';
import {
  createColumnHelper,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';

import { LicenseFinding } from '@/api';
import { getRunDetectedLicenseFindingsOptions } from '@/api/@tanstack/react-query.gen';
import { BreakableString } from '@/components/breakable-string';
import { DataTable } from '@/components/data-table/data-table';
import { LoadingIndicator } from '@/components/loading-indicator';
import { toastError } from '@/lib/toast';

const findingColumnHelper = createColumnHelper<LicenseFinding>();
const defaultPageSize = 10;
const licenseFindingsRoutePath =
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/license-findings/';

type DetectedLicenseFindingsTableProps = {
  runId: number;
  license: string;
  identifier: string;
};

export const DetectedLicenseFindingsTable = ({
  runId,
  license,
  identifier,
}: DetectedLicenseFindingsTableProps) => {
  const search = useSearch({ from: licenseFindingsRoutePath });
  const findingsPageIndex = search.findingsPage ? search.findingsPage - 1 : 0;
  const findingsPageSize = search.findingsPageSize || defaultPageSize;

  const {
    data: findings,
    isPending,
    isError,
    error,
  } = useQuery({
    ...getRunDetectedLicenseFindingsOptions({
      path: {
        runId,
        license,
        identifier,
      },
      query: {
        limit: findingsPageSize,
        offset: findingsPageIndex * findingsPageSize,
      },
    }),
  });

  const findingColumns = [
    findingColumnHelper.accessor('path', {
      id: 'path',
      header: 'Path',
      cell: ({ row }) => <BreakableString text={row.original.path} />,
      meta: {
        isGrow: true,
      },
    }),
    findingColumnHelper.accessor('startLine', {
      id: 'startLine',
      header: 'Start Line',
      meta: {
        widthPercentage: 10,
      },
    }),
    findingColumnHelper.accessor('endLine', {
      id: 'endLine',
      header: 'End Line',
      meta: {
        widthPercentage: 10,
      },
    }),
    findingColumnHelper.accessor('score', {
      id: 'score',
      header: 'Score',
      meta: {
        widthPercentage: 10,
      },
      cell: ({ row }) => row.original.score ?? '-',
    }),
    findingColumnHelper.accessor('scanner', {
      id: 'scanner',
      header: 'Scanner',
      meta: {
        widthPercentage: 15,
      },
    }),
  ];

  const findingsTable = useReactTable({
    data: findings?.data || [],
    columns: findingColumns,
    pageCount: Math.ceil(
      (findings?.pagination.totalCount ?? 0) / findingsPageSize
    ),
    state: {
      pagination: {
        pageIndex: findingsPageIndex,
        pageSize: findingsPageSize,
      },
    },
    getCoreRowModel: getCoreRowModel(),
    manualPagination: true,
  });

  if (isPending) {
    return <LoadingIndicator />;
  }

  if (isError) {
    toastError('Unable to load data', error);
    return <></>;
  }

  return (
    <div className='space-y-2 p-2'>
      <div className='text-muted-foreground text-sm'>
        License findings ({findings.pagination.totalCount} in total).
      </div>
      <DataTable
        table={findingsTable}
        className='[&_tbody_tr:first-child]:border-t'
        setCurrentPageOptions={(currentPage) => {
          return {
            to: '.',
            search: {
              ...search,
              findingsPage: currentPage,
            },
          };
        }}
        setPageSizeOptions={(size) => {
          return {
            to: '.',
            search: {
              ...search,
              findingsPage: 1,
              findingsPageSize: size,
            },
          };
        }}
      />
    </div>
  );
};
