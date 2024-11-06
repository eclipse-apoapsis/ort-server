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
  ColumnDef,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { EditIcon, PlusIcon, Repeat, View } from 'lucide-react';

import {
  useRepositoriesServiceDeleteRepositoryById,
  useRepositoriesServiceGetOrtRunsByRepositoryId,
  useRepositoriesServiceGetRepositoryById,
} from '@/api/queries';
import {
  prefetchUseRepositoriesServiceGetOrtRunsByRepositoryId,
  prefetchUseRepositoriesServiceGetRepositoryById,
} from '@/api/queries/prefetch';
import { ApiError, GetOrtRunsByRepositoryIdResponse } from '@/api/requests';
import { DataTable } from '@/components/data-table/data-table';
import { DeleteDialog } from '@/components/delete-dialog';
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
import { paginationSchema } from '@/schemas';

const defaultPageSize = 10;
const pollInterval = config.pollInterval;

const columns: ColumnDef<GetOrtRunsByRepositoryIdResponse['data'][number]>[] = [
  {
    accessorKey: 'runIndex',
    header: () => <div>Run</div>,
    cell: ({ row }) => (
      <Link
        className='font-semibold text-blue-400 hover:underline'
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
        {row.original.index}
      </Link>
    ),
    size: 50,
  },
  {
    accessorKey: 'createdAt',
    header: () => <div>Created At</div>,
    cell: ({ row }) => <TimestampWithUTC timestamp={row.original.createdAt} />,
  },
  {
    accessorKey: 'runStatus',
    header: () => <div>Run Status</div>,
    cell: ({ row }) => (
      <Badge
        className={`border ${getStatusBackgroundColor(row.original.status)}`}
      >
        {row.original.status}
      </Badge>
    ),
  },
  {
    accessorKey: 'jobStatuses',
    header: () => <div>Job Status</div>,
    cell: ({ row }) => (
      <OrtRunJobStatus
        jobs={row.original.jobs}
        orgId={row.original.organizationId.toString()}
        productId={row.original.productId.toString()}
        repoId={row.original.repositoryId.toString()}
        runIndex={row.original.index.toString()}
      />
    ),
  },
  {
    accessorKey: 'duration',
    header: () => <div>Duration</div>,
    cell: ({ row }) => (
      <RunDuration
        createdAt={row.original.createdAt}
        finishedAt={row.original.finishedAt ?? undefined}
      />
    ),
  },
  {
    accessorKey: 'actions',
    header: () => <div>Actions</div>,
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
        <Tooltip>
          <TooltipTrigger asChild>
            <Button variant='outline' asChild size='sm'>
              <Link
                to='/organizations/$orgId/products/$productId/repositories/$repoId/create-run'
                params={{
                  orgId: row.original.organizationId.toString(),
                  productId: row.original.productId.toString(),
                  repoId: row.original.repositoryId.toString(),
                }}
                search={{
                  rerunIndex: row.original.index,
                }}
              >
                Rerun
                <Repeat className='ml-1 h-4 w-4' />
              </Link>
            </Button>
          </TooltipTrigger>
          <TooltipContent>
            Create a new ORT run based on this run
          </TooltipContent>
        </Tooltip>
      </div>
    ),
  },
];

const RepoComponent = () => {
  const params = Route.useParams();
  const navigate = Route.useNavigate();
  const search = Route.useSearch();
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : defaultPageSize;

  const {
    data: repo,
    error: repoError,
    isPending: repoIsPending,
    isError: repoIsError,
  } = useRepositoriesServiceGetRepositoryById({
    repositoryId: Number.parseInt(params.repoId),
  });

  const {
    data: runs,
    error: runsError,
    isPending: runsIsPending,
    isError: runsIsError,
  } = useRepositoriesServiceGetOrtRunsByRepositoryId(
    {
      repositoryId: Number.parseInt(params.repoId),
      limit: pageSize,
      offset: pageIndex * pageSize,
      sort: '-index',
    },
    undefined,
    {
      refetchInterval: pollInterval,
    }
  );

  const { mutateAsync: deleteRepository, isPending } =
    useRepositoriesServiceDeleteRepositoryById({
      onSuccess() {
        toast.info('Delete Repository', {
          description: `Repository "${repo?.url}" deleted successfully.`,
        });
        navigate({
          to: '/organizations/$orgId/products/$productId',
          params: { orgId: params.orgId, productId: params.productId },
        });
      },
      onError(error: ApiError) {
        toast.error(error.message, {
          description: <ToastError error={error} />,
          duration: Infinity,
          cancel: {
            label: 'Dismiss',
            onClick: () => {},
          },
        });
      },
    });

  async function handleDelete() {
    await deleteRepository({
      repositoryId: Number.parseInt(params.repoId),
    });
  }

  const table = useReactTable({
    data: runs?.data || [],
    columns,
    pageCount: Math.ceil((runs?.pagination.totalCount ?? 0) / pageSize),
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
    },
    getCoreRowModel: getCoreRowModel(),
    manualPagination: true,
  });

  if (repoIsPending || runsIsPending) {
    return <LoadingIndicator />;
  }

  if (repoIsError || runsIsError) {
    toast.error('Unable to load data', {
      description: <ToastError error={repoError || runsError} />,
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
        <CardTitle className='flex flex-row justify-between'>
          <div className='flex items-stretch'>
            <div className='flex items-center pb-1'>{repo.url}</div>
            <Tooltip>
              <TooltipTrigger>
                <Button
                  asChild
                  size='sm'
                  variant='outline'
                  className='ml-2 px-2'
                >
                  <Link
                    to='/organizations/$orgId/products/$productId/repositories/$repoId/edit'
                    params={{
                      orgId: params.orgId,
                      productId: params.productId,
                      repoId: params.repoId,
                    }}
                  >
                    <EditIcon className='h-4 w-4' />
                  </Link>
                </Button>
              </TooltipTrigger>
              <TooltipContent>Edit this repository</TooltipContent>
            </Tooltip>
          </div>
          <DeleteDialog
            item={{
              descriptor: 'repository',
              name: repo.url,
            }}
            onDelete={handleDelete}
            isPending={isPending}
          />
        </CardTitle>
        <CardDescription>{repo.type}</CardDescription>
        <div className='py-2'>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button asChild size='sm' className='ml-auto gap-1'>
                <Link
                  to='/organizations/$orgId/products/$productId/repositories/$repoId/create-run'
                  params={{
                    orgId: params.orgId,
                    productId: params.productId,
                    repoId: params.repoId,
                  }}
                >
                  New run
                  <PlusIcon className='h-4 w-4' />
                </Link>
              </Button>
            </TooltipTrigger>
            <TooltipContent>
              Create a new ORT run for this repository
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
  '/_layout/organizations/$orgId/products/$productId/repositories/$repoId/'
)({
  validateSearch: paginationSchema,
  loaderDeps: ({ search: { page, pageSize } }) => ({ page, pageSize }),
  loader: async ({ context, params, deps: { page, pageSize } }) => {
    await Promise.allSettled([
      prefetchUseRepositoriesServiceGetRepositoryById(context.queryClient, {
        repositoryId: Number.parseInt(params.repoId),
      }),
      prefetchUseRepositoriesServiceGetOrtRunsByRepositoryId(
        context.queryClient,
        {
          repositoryId: Number.parseInt(params.repoId),
          limit: pageSize || defaultPageSize,
          offset: page ? (page - 1) * (pageSize || defaultPageSize) : 0,
          sort: '-index',
        }
      ),
    ]);
  },
  component: RepoComponent,
  pendingComponent: LoadingIndicator,
});
