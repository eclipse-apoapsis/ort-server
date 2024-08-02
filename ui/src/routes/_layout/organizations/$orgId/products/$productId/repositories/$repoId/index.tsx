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

import { useSuspenseQueries } from '@tanstack/react-query';
import { createFileRoute, Link, useNavigate } from '@tanstack/react-router';
import {
  ColumnDef,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { EditIcon, PlusIcon, Redo2 } from 'lucide-react';

import {
  useRepositoriesServiceDeleteRepositoryById,
  useRepositoriesServiceGetOrtRunsKey,
  useRepositoriesServiceGetRepositoryByIdKey,
} from '@/api/queries';
import {
  ApiError,
  GetOrtRunsResponse,
  RepositoriesService,
} from '@/api/requests';
import { DataTable } from '@/components/data-table/data-table';
import { DeleteDialog } from '@/components/delete-dialog';
import { LoadingIndicator } from '@/components/loading-indicator';
import { OrtRunJobStatus } from '@/components/ort-run-job-status';
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
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { useToast } from '@/components/ui/use-toast';
import { calculateDuration } from '@/helpers/get-run-duration';
import { getStatusBackgroundColor } from '@/helpers/get-status-colors';
import { paginationSchema } from '@/schemas';

const defaultPageSize = 10;

const pollInterval =
  Number.parseInt(import.meta.env.VITE_RUN_POLL_INTERVAL) || 10000;

const columns: ColumnDef<GetOrtRunsResponse['data'][number]>[] = [
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
    cell: ({ row }) => (
      <div>
        {new Date(row.original.createdAt).toLocaleString(navigator.language)}
      </div>
    ),
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
    cell: ({ row }) => <OrtRunJobStatus jobs={row.original.jobs} />,
  },
  {
    accessorKey: 'duration',
    header: () => <div>Duration</div>,
    cell: ({ row }) =>
      row.original.finishedAt
        ? calculateDuration(row.original.createdAt, row.original.finishedAt)
        : '-',
  },
  {
    accessorKey: 'actions',
    header: () => <div>Actions</div>,
    cell: ({ row }) => (
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
              <Redo2 className='ml-1 h-4 w-4' />
            </Link>
          </Button>
        </TooltipTrigger>
        <TooltipContent>Create a new ORT run based on this run</TooltipContent>
      </Tooltip>
    ),
  },
];

const RepoComponent = () => {
  const params = Route.useParams();
  const navigate = useNavigate();
  const { toast } = useToast();
  const search = Route.useSearch();
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : defaultPageSize;

  const [{ data: repo }, { data: runs }] = useSuspenseQueries({
    queries: [
      {
        queryKey: [useRepositoriesServiceGetRepositoryByIdKey, params.repoId],
        queryFn: async () =>
          await RepositoriesService.getRepositoryById({
            repositoryId: Number.parseInt(params.repoId),
          }),
      },
      {
        queryKey: [
          useRepositoriesServiceGetOrtRunsKey,
          params.repoId,
          pageIndex,
          pageSize,
        ],
        queryFn: async () =>
          await RepositoriesService.getOrtRuns({
            repositoryId: Number.parseInt(params.repoId),
            limit: pageSize,
            offset: pageIndex * pageSize,
            sort: '-index',
          }),
        refetchInterval: pollInterval,
      },
    ],
  });

  const { mutateAsync: deleteRepository, isPending } =
    useRepositoriesServiceDeleteRepositoryById({
      onSuccess() {
        toast({
          title: 'Delete Repository',
          description: `Repository "${repo.url}" deleted successfully.`,
        });
        navigate({
          to: '/organizations/$orgId/products/$productId',
          params: { orgId: params.orgId, productId: params.productId },
        });
      },
      onError(error: ApiError) {
        toast({
          title: error.message,
          description: <ToastError error={error} />,
          variant: 'destructive',
        });
      },
    });

  async function handleDelete() {
    await deleteRepository({
      repositoryId: Number.parseInt(params.repoId),
    });
  }

  const table = useReactTable({
    data: runs.data || [],
    columns,
    pageCount: Math.ceil(runs.pagination.totalCount / pageSize),
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
    <TooltipProvider>
      <Card className='mx-auto w-full max-w-4xl'>
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
          <DataTable table={table} />
        </CardContent>
      </Card>
    </TooltipProvider>
  );
};

export const Route = createFileRoute(
  '/_layout/organizations/$orgId/products/$productId/repositories/$repoId/'
)({
  validateSearch: paginationSchema,
  loaderDeps: ({ search: { page, pageSize } }) => ({ page, pageSize }),
  loader: async ({ context, params, deps: { page, pageSize } }) => {
    await Promise.allSettled([
      context.queryClient.ensureQueryData({
        queryKey: [useRepositoriesServiceGetRepositoryByIdKey, params.repoId],
        queryFn: () =>
          RepositoriesService.getRepositoryById({
            repositoryId: Number.parseInt(params.repoId),
          }),
      }),
      context.queryClient.ensureQueryData({
        queryKey: [
          useRepositoriesServiceGetOrtRunsKey,
          params.repoId,
          page,
          pageSize,
        ],
        queryFn: () =>
          RepositoriesService.getOrtRuns({
            repositoryId: Number.parseInt(params.repoId),
            limit: pageSize || defaultPageSize,
            offset: page ? (page - 1) * (pageSize || defaultPageSize) : 0,
            sort: '-index',
          }),
      }),
    ]);
  },
  component: RepoComponent,
  pendingComponent: LoadingIndicator,
});
