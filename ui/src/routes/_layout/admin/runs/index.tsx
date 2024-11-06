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
import { View } from 'lucide-react';

import {
  useOrganizationsServiceGetOrganizationById,
  useProductsServiceGetProductById,
  useRepositoriesServiceGetRepositoryById,
  useRunsServiceGetOrtRuns,
} from '@/api/queries';
import { prefetchUseRunsServiceGetOrtRuns } from '@/api/queries/prefetch';
import { OrtRunSummary } from '@/api/requests';
import { DataTable } from '@/components/data-table/data-table';
import { DataTableToolbar } from '@/components/data-table/data-table-toolbar';
import { FilterMultiSelect } from '@/components/data-table/filter-multi-select';
import { LoadingIndicator } from '@/components/loading-indicator';
import { OrtRunJobStatus } from '@/components/ort-run-job-status';
import { RunDuration } from '@/components/run-duration';
import { TimestampWithUTC } from '@/components/timestamp-with-utc';
import { ToastError } from '@/components/toast-error';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { config } from '@/config';
import { getStatusBackgroundColor } from '@/helpers/get-status-class';
import { toast } from '@/lib/toast';
import { ortRunStatus, paginationSchema, statusSchema } from '@/schemas';

const defaultPageSize = 10;
const pollInterval = config.pollInterval;

const columnHelper = createColumnHelper<OrtRunSummary>();

const columns = [
  columnHelper.display({
    id: 'repository',
    header: 'Repository',
    cell: function CellComponent({ row }) {
      const { data: repo } = useRepositoriesServiceGetRepositoryById({
        repositoryId: row.original.repositoryId,
      });

      const { data: org } = useOrganizationsServiceGetOrganizationById({
        organizationId: row.original.organizationId,
      });

      const { data: prod } = useProductsServiceGetProductById({
        productId: row.original.productId,
      });

      return (
        <div>
          <Link
            className='block font-semibold text-blue-400 hover:underline'
            to={
              '/organizations/$orgId/products/$productId/repositories/$repoId'
            }
            params={{
              orgId: row.original.organizationId.toString(),
              productId: row.original.productId.toString(),
              repoId: row.original.repositoryId.toString(),
            }}
          >
            {repo?.url}
          </Link>
          <div className='text-xs italic text-slate-500'>
            in{' '}
            <Link
              className='hover:underline'
              to={'/organizations/$orgId'}
              params={{ orgId: row.original.organizationId.toString() }}
            >
              {org?.name}
            </Link>
            /
            <Link
              className='hover:underline'
              to={'/organizations/$orgId/products/$productId'}
              params={{
                orgId: row.original.organizationId.toString(),
                productId: row.original.productId.toString(),
              }}
            >
              {prod?.name}
            </Link>
          </div>
        </div>
      );
    },
  }),
  columnHelper.accessor('createdAt', {
    header: 'Created At',
    cell: ({ row }) => <TimestampWithUTC timestamp={row.original.createdAt} />,
    size: 95,
  }),
  columnHelper.accessor('finishedAt', {
    header: 'Finished At',
    cell: ({ row }) =>
      row.original.finishedAt ? (
        <TimestampWithUTC timestamp={row.original.finishedAt} />
      ) : (
        <span className='italic'>Not finished yet</span>
      ),
    size: 95,
  }),
  columnHelper.accessor('status', {
    header: 'Run Status',
    cell: ({ row }) => (
      <Badge
        className={`border ${getStatusBackgroundColor(row.original.status)}`}
      >
        {row.original.status}
      </Badge>
    ),
  }),
  columnHelper.display({
    id: 'jobStatuses',
    header: 'Job Status',
    cell: ({ row }) => (
      <OrtRunJobStatus
        jobs={row.original.jobs}
        orgId={row.original.organizationId.toString()}
        productId={row.original.productId.toString()}
        repoId={row.original.repositoryId.toString()}
        runIndex={row.original.index.toString()}
      />
    ),
    size: 100,
  }),
  columnHelper.display({
    id: 'duration',
    header: 'Duration',
    cell: ({ row }) => (
      <RunDuration
        createdAt={row.original.createdAt}
        finishedAt={row.original.finishedAt ?? undefined}
      />
    ),
    size: 100,
  }),
  columnHelper.display({
    id: 'actions',
    header: 'Actions',
    cell: ({ row }) => (
      <div className='flex gap-2'>
        <Tooltip>
          <TooltipTrigger asChild>
            <Button variant='outline' asChild size='sm'>
              <Link
                to={
                  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex'
                }
                params={{
                  orgId: row.original.organizationId.toString(),
                  productId: row.original.productId.toString(),
                  repoId: row.original.repositoryId.toString(),
                  runIndex: row.original.index.toString(),
                }}
              >
                View
                <View className='ml-1 h-4 w-4' />
              </Link>
            </Button>
          </TooltipTrigger>
          <TooltipContent>View the details of this run</TooltipContent>
        </Tooltip>
      </div>
    ),
    size: 90,
  }),
];

const RunsComponent = () => {
  const search = Route.useSearch();
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : defaultPageSize;
  const status = search.status;
  const navigate = Route.useNavigate();

  const { data, error } = useRunsServiceGetOrtRuns(
    {
      limit: pageSize,
      offset: pageIndex * pageSize,
      status: status?.join(','),
    },
    undefined,
    {
      refetchInterval: pollInterval,
    }
  );

  const table = useReactTable({
    data: data?.data || [],
    columns,
    pageCount: Math.ceil((data?.pagination.totalCount ?? 0) / pageSize),
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
    },
    getCoreRowModel: getCoreRowModel(),
    manualPagination: true,
  });

  if (error) {
    toast.error('Unable to load data', {
      description: <ToastError error={error} />,
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
        <CardTitle>Runs</CardTitle>
        <CardDescription className='sr-only'>
          A list of all runs.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <DataTableToolbar
          filters={
            <FilterMultiSelect
              title='Run Status'
              options={ortRunStatus.options.map((status) => ({
                label: status,
                value: status,
              }))}
              selected={status || []}
              setSelected={(statuses) => {
                navigate({
                  search: {
                    ...search,
                    page: 1,
                    status: statuses.length === 0 ? undefined : statuses,
                  },
                });
              }}
            />
          }
          resetFilters={() => {
            navigate({
              search: { ...search, page: 1, status: undefined },
            });
          }}
          resetBtnVisible={status !== undefined}
          className='mb-2'
        />
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

export const Route = createFileRoute('/_layout/admin/runs/')({
  validateSearch: paginationSchema.merge(statusSchema),
  loaderDeps: ({ search: { page, pageSize, status } }) => ({
    page,
    pageSize,
    status,
  }),
  loader: async ({ context, deps: { page, pageSize, status } }) => {
    await prefetchUseRunsServiceGetOrtRuns(context.queryClient, {
      limit: pageSize || defaultPageSize,
      offset: page ? (page - 1) * (pageSize || defaultPageSize) : 0,
      status: status?.join(','),
    });
  },
  component: RunsComponent,
  pendingComponent: LoadingIndicator,
});
