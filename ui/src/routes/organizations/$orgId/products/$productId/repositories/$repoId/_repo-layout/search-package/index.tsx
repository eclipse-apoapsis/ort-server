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
import { createFileRoute, Link, useNavigate } from '@tanstack/react-router';
import {
  createColumnHelper,
  getCoreRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { useMemo } from 'react';
import z from 'zod';

import { getRunsWithPackageOptions } from '@/api/@tanstack/react-query.gen';
import { RunWithPackage } from '@/api/types.gen';
import { BreakableString } from '@/components/breakable-string';
import { DataTable } from '@/components/data-table/data-table';
import { RegexForm } from '@/components/form/regex-form';
import { LoadingIndicator } from '@/components/loading-indicator';
import { TimestampWithUTC } from '@/components/timestamp-with-utc';
import { ToastError } from '@/components/toast-error';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { updateColumnSorting } from '@/helpers/handle-multisort';
import { identifierToString } from '@/helpers/identifier-conversion';
import { toast } from '@/lib/toast';
import {
  packageIdentifierSearchParameterSchema,
  paginationSearchParameterSchema,
  sortingSearchParameterSchema,
} from '@/schemas';
import { useUserSettingsStore } from '@/store/user-settings.store';

const defaultPageSize = 10;
const columnHelper = createColumnHelper<RunWithPackage>();

function SearchPackageComponent() {
  const params = Route.useParams();
  const search = Route.useSearch();
  const navigate = useNavigate();
  const identifier = search.pkgId ?? '';
  const packageIdType = useUserSettingsStore((state) => state.packageIdType);

  const columns = [
    columnHelper.accessor('createdAt', {
      header: 'Created At',
      cell: ({ row }) => (
        <TimestampWithUTC timestamp={row.original.createdAt} />
      ),
    }),
    columnHelper.accessor('revision', {
      header: 'Revision',
    }),
    columnHelper.accessor('ortRunIndex', {
      header: 'Run Index',
      maxSize: 100,
      cell: function CellComponent({ row }) {
        return (
          <div>
            <Link
              className='block text-blue-400 hover:underline'
              to={
                '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex'
              }
              params={{
                orgId: row.original.organizationId.toString(),
                productId: row.original.productId.toString(),
                repoId: row.original.repositoryId.toString(),
                runIndex: row.original.ortRunIndex.toString(),
              }}
            >
              {row.original.ortRunIndex}
            </Link>
          </div>
        );
      },
    }),
    columnHelper.accessor('packageId', {
      header: 'Matching Package',
      cell: function CellComponent({ row }) {
        const id =
          packageIdType === 'PURL'
            ? row.original.purl
            : identifierToString(row.original.packageId);
        return (
          <Link
            className='font-semibold text-blue-400 hover:underline'
            to='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/packages'
            params={{
              orgId: row.original.organizationId.toString(),
              productId: row.original.productId.toString(),
              repoId: row.original.repositoryId.toString(),
              runIndex: row.original.ortRunIndex.toString(),
            }}
            search={{
              pkgId: id ?? undefined,
              marked: '0',
            }}
          >
            <BreakableString text={id ?? ''} />
          </Link>
        );
      },
    }),
  ];

  const pageIndex = useMemo(
    () => (search.page ? search.page - 1 : 0),
    [search.page]
  );

  const pageSize = useMemo(
    () => (search.pageSize ? search.pageSize : defaultPageSize),
    [search.pageSize]
  );

  const sortBy = useMemo(
    () => (search.sortBy ? search.sortBy : undefined),
    [search.sortBy]
  );

  const {
    data: runsWithPackage,
    isPending: isRunsPending,
    isError: isRunsError,
    error: runsError,
  } = useQuery({
    ...getRunsWithPackageOptions({
      query: {
        ...(packageIdType === 'PURL'
          ? { purl: identifier }
          : { identifier: identifier }),
        repositoryId: Number.parseInt(params.repoId),
      },
    }),
    enabled: identifier !== '',
  });

  const table = useReactTable({
    data: runsWithPackage || [],
    columns,
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
      sorting: sortBy,
    },
    getCoreRowModel: getCoreRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    getSortedRowModel: getSortedRowModel(),
  });

  if (isRunsError) {
    toast.error('Unable to load data', {
      description: <ToastError error={runsError} />,
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
  }

  return (
    <Card className='h-fit'>
      <CardHeader>
        <CardTitle>Search Package</CardTitle>
        <CardDescription>
          Search for ORT runs of this repository that contain matching packages.
        </CardDescription>
      </CardHeader>
      <RegexForm
        className='mx-12'
        initialValue={identifier}
        onRegexChange={(regex) => {
          navigate({
            to: Route.to,
            params,
            search: { ...search, pkgId: regex || undefined, page: 1 },
          });
        }}
      />
      <CardContent>
        {isRunsPending && identifier ? (
          <LoadingIndicator />
        ) : (
          <DataTable
            table={table}
            setCurrentPageOptions={(currentPage) => {
              return {
                to: Route.to,
                search: { ...search, page: currentPage },
              };
            }}
            setPageSizeOptions={(size) => {
              return {
                to: Route.to,
                search: { ...search, page: 1, pageSize: size },
              };
            }}
            setSortingOptions={(sortBy) => {
              return {
                to: Route.to,
                search: {
                  ...search,
                  sortBy: updateColumnSorting(search.sortBy, sortBy),
                },
              };
            }}
          />
        )}
      </CardContent>
    </Card>
  );
}

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/search-package/'
)({
  validateSearch: z.object({
    ...paginationSearchParameterSchema.shape,
    ...sortingSearchParameterSchema.shape,
    ...packageIdentifierSearchParameterSchema.shape,
  }),
  component: SearchPackageComponent,
  pendingComponent: LoadingIndicator,
});
