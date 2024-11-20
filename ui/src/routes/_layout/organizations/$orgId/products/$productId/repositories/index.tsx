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

import { createFileRoute, Link } from '@tanstack/react-router';
import {
  createColumnHelper,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { PlusIcon } from 'lucide-react';

import {
  useProductsServiceGetProductById,
  useRepositoriesServiceGetRepositoriesByProductId,
} from '@/api/queries';
import {
  prefetchUseProductsServiceGetProductById,
  prefetchUseRepositoriesServiceGetRepositoriesByProductId,
} from '@/api/queries/prefetch';
import { Repository } from '@/api/requests';
import { DataTable } from '@/components/data-table/data-table';
import { LoadingIndicator } from '@/components/loading-indicator';
import { ToastError } from '@/components/toast-error';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { toast } from '@/lib/toast';
import { paginationSearchParameterSchema } from '@/schemas';
import { LastJobStatus } from './-components/last-job-status';
import { LastRunDate } from './-components/last-run-date';
import { LastRunStatus } from './-components/last-run-status';
import { TotalRuns } from './-components/total-runs';

const defaultPageSize = 10;

const columnHelper = createColumnHelper<Repository>();

// In anticipation of these column definitions to be changed later, when the corresponding
// endpoint is implemented, columnHelper.accessor() is only used when the data being
// shown contains data from the column helper type.

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
    }
  ),
  columnHelper.display({
    id: 'runs',
    header: 'Runs',
    size: 60,
    cell: ({ row }) => <TotalRuns repoId={row.original.id} />,
  }),
  columnHelper.display({
    id: 'runStatus',
    header: 'Last Run Status',
    cell: ({ row }) => <LastRunStatus repoId={row.original.id} />,
  }),
  columnHelper.display({
    id: 'lastRunDate',
    header: 'Last Run Date',
    cell: ({ row }) => <LastRunDate repoId={row.original.id} />,
  }),
  columnHelper.display({
    id: 'jobStatus',
    header: 'Last Job Status',
    cell: ({ row }) => <LastJobStatus repoId={row.original.id} />,
  }),
];

const RepositoriesComponent = () => {
  const params = Route.useParams();
  const search = Route.useSearch();
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : defaultPageSize;

  const {
    data: product,
    error: prodError,
    isPending: prodIsPending,
    isError: prodIsError,
  } = useProductsServiceGetProductById({
    productId: Number.parseInt(params.productId),
  });

  const {
    data: repositories,
    error: reposError,
    isPending: reposIsPending,
    isError: reposIsError,
  } = useRepositoriesServiceGetRepositoriesByProductId({
    productId: Number.parseInt(params.productId),
    limit: pageSize,
    offset: pageIndex * pageSize,
  });

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

  if (prodIsPending || reposIsPending) {
    return <LoadingIndicator />;
  }

  if (prodIsError || reposIsError) {
    toast.error('Unable to load data', {
      description: <ToastError error={prodError || reposError} />,
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
    return;
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Repositories belonging to {product.name}</CardTitle>
        <div className='py-4'>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button asChild size='sm' className='ml-auto gap-1'>
                <Link
                  to='/organizations/$orgId/products/$productId/create-repository'
                  params={{
                    orgId: params.orgId,
                    productId: params.productId,
                  }}
                >
                  Add repository
                  <PlusIcon className='h-4 w-4' />
                </Link>
              </Button>
            </TooltipTrigger>
            <TooltipContent>
              Add a repository for managing compliance runs
            </TooltipContent>
          </Tooltip>
        </div>
      </CardHeader>
      <CardContent>
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
        />
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute(
  '/_layout/organizations/$orgId/products/$productId/repositories/'
)({
  validateSearch: paginationSearchParameterSchema,
  loaderDeps: ({ search: { page, pageSize } }) => ({ page, pageSize }),
  loader: async ({ context, params, deps: { page, pageSize } }) => {
    await Promise.allSettled([
      prefetchUseProductsServiceGetProductById(context.queryClient, {
        productId: Number.parseInt(params.productId),
      }),
      prefetchUseRepositoriesServiceGetRepositoriesByProductId(
        context.queryClient,
        {
          productId: Number.parseInt(params.productId),
          limit: pageSize || defaultPageSize,
          offset: page ? (page - 1) * (pageSize || defaultPageSize) : 0,
        }
      ),
    ]);
  },
  component: RepositoriesComponent,
  pendingComponent: LoadingIndicator,
});
