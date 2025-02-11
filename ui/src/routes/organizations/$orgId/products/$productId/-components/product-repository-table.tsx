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

import { getRouteApi, Link } from '@tanstack/react-router';
import {
  createColumnHelper,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';

import { useRepositoriesServiceGetApiV1ProductsByProductIdRepositories } from '@/api/queries';
import { PagedResponse_Repository, Repository } from '@/api/requests';
import { DataTable } from '@/components/data-table/data-table';
import { LoadingIndicator } from '@/components/loading-indicator';
import { ToastError } from '@/components/toast-error';
import { toast } from '@/lib/toast';
import { useTablePrefsStore } from '@/store/table-prefs.store';
import { LastJobStatus } from './last-job-status';
import { LastRunDate } from './last-run-date';
import { LastRunStatus } from './last-run-status';
import { TotalRuns } from './total-runs';

const columnHelper = createColumnHelper<Repository>();

const columns = [
  columnHelper.accessor(
    ({ url, type }) => {
      return url + type;
    },
    {
      id: 'repository',
      header: 'Repositories',
      cell: ({ row }) => (
        <>
          <Link
            className='block font-semibold text-blue-400 hover:underline'
            to={
              '/organizations/$orgId/products/$productId/repositories/$repoId'
            }
            params={{
              orgId: row.original.organizationId.toString(),
              productId: row.original.productId.toString(),
              repoId: row.original.id.toString(),
            }}
          >
            {row.original.url}
          </Link>
          <div className='text-sm text-muted-foreground md:inline'>
            {row.original.type}
          </div>
        </>
      ),
      enableColumnFilter: false,
    }
  ),
  columnHelper.display({
    id: 'runs',
    header: 'Runs',
    size: 60,
    cell: ({ row }) => (
      <Link
        to='/organizations/$orgId/products/$productId/repositories/$repoId/runs'
        params={{
          orgId: row.original.organizationId.toString(),
          productId: row.original.productId.toString(),
          repoId: row.original.id.toString(),
        }}
        className='font-semibold text-blue-400 hover:underline'
      >
        <TotalRuns repoId={row.original.id} />
      </Link>
    ),
    enableColumnFilter: false,
  }),
  columnHelper.display({
    id: 'runStatus',
    header: 'Last Run Status',
    cell: ({ row }) => <LastRunStatus repoId={row.original.id} />,
    enableColumnFilter: false,
  }),
  columnHelper.display({
    id: 'lastRunDate',
    header: 'Last Run Date',
    cell: ({ row }) => <LastRunDate repoId={row.original.id} />,
    enableColumnFilter: false,
  }),
  columnHelper.display({
    id: 'jobStatus',
    header: 'Last Job Status',
    cell: ({ row }) => <LastJobStatus repoId={row.original.id} />,
    enableColumnFilter: false,
  }),
];

const routeApi = getRouteApi('/organizations/$orgId/products/$productId/');

export const ProductRepositoryTable = () => {
  const repoPageSize = useTablePrefsStore((state) => state.repoPageSize);
  const params = routeApi.useParams();
  const search = routeApi.useSearch();
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : repoPageSize;

  const {
    data: repositories,
    error: reposError,
    isPending: reposIsPending,
    isError: reposIsError,
  } = useRepositoriesServiceGetApiV1ProductsByProductIdRepositories({
    productId: Number.parseInt(params.productId),
    limit: pageSize,
    offset: pageIndex * pageSize,
  });

  if (reposIsPending) {
    return <LoadingIndicator />;
  }

  if (reposIsError) {
    toast.error('Unable to load data', {
      description: <ToastError error={reposError} />,
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
    return;
  }

  return (
    <ProductRepositoryTableInner
      repositories={repositories}
      repoPageSize={repoPageSize}
    />
  );
};

const ProductRepositoryTableInner = ({
  repositories,
  repoPageSize,
}: {
  repositories: PagedResponse_Repository;
  repoPageSize: number;
}) => {
  const setRepoPageSize = useTablePrefsStore((state) => state.setRepoPageSize);
  const search = routeApi.useSearch();
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : repoPageSize;

  const table = useReactTable({
    data: repositories?.data || [],
    columns,
    pageCount: Math.ceil((repositories?.pagination.totalCount ?? 0) / pageSize),
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
    },
    getCoreRowModel: getCoreRowModel(),
    manualPagination: true,
  });

  return (
    <DataTable
      table={table}
      setCurrentPageOptions={(currentPage) => {
        return {
          search: { ...search, page: currentPage },
        };
      }}
      setPageSizeOptions={(size) => {
        setRepoPageSize(size);
        return {
          search: { ...search, page: 1, pageSize: size },
        };
      }}
    />
  );
};
