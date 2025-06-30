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

import { useQueryClient } from '@tanstack/react-query';
import { createFileRoute, Link } from '@tanstack/react-router';
import {
  CellContext,
  ColumnDef,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { EditIcon, PlusIcon } from 'lucide-react';

import {
  useRepositoriesServiceDeleteApiV1RepositoriesByRepositoryIdInfrastructureServicesByServiceName,
  useRepositoriesServiceGetApiV1RepositoriesByRepositoryId,
  useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdInfrastructureServices,
  useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdInfrastructureServicesKey,
} from '@/api/queries';
import {
  prefetchUseRepositoriesServiceGetApiV1RepositoriesByRepositoryId,
  prefetchUseRepositoriesServiceGetApiV1RepositoriesByRepositoryIdInfrastructureServices,
} from '@/api/queries/prefetch';
import { ApiError, InfrastructureService } from '@/api/requests';
import { DataTable } from '@/components/data-table/data-table';
import { DeleteDialog } from '@/components/delete-dialog';
import { DeleteIconButton } from '@/components/delete-icon-button';
import { LoadingIndicator } from '@/components/loading-indicator';
import { ToastError } from '@/components/toast-error';
import { Button } from '@/components/ui/button';
import { buttonVariants } from '@/components/ui/button-variants';
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
import { toast } from '@/lib/toast';
import { cn } from '@/lib/utils';
import { paginationSearchParameterSchema } from '@/schemas';

const defaultPageSize = 10;

const ActionCell = ({ row }: CellContext<InfrastructureService, unknown>) => {
  const params = Route.useParams();
  const queryClient = useQueryClient();

  const { mutateAsync: delService } =
    useRepositoriesServiceDeleteApiV1RepositoriesByRepositoryIdInfrastructureServicesByServiceName(
      {
        onSuccess() {
          toast.info('Delete Infrastructure Service', {
            description: `Infrastructure service "${row.original.name}" deleted successfully.`,
          });
          queryClient.invalidateQueries({
            queryKey: [
              useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdInfrastructureServicesKey,
            ],
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
      }
    );

  return (
    <div className='flex justify-end gap-1'>
      <Tooltip>
        <TooltipTrigger asChild>
          <Link
            to='/organizations/$orgId/products/$productId/repositories/$repoId/infrastructure-services/$serviceName/edit'
            params={{
              orgId: params.orgId,
              productId: params.productId,
              repoId: params.repoId,
              serviceName: row.original.name,
            }}
            className={cn(buttonVariants({ variant: 'outline' }), 'h-8 px-2')}
          >
            <span className='sr-only'>Edit</span>
            <EditIcon size={16} />
          </Link>
        </TooltipTrigger>
        <TooltipContent>Edit this infrastructure service</TooltipContent>
      </Tooltip>

      <DeleteDialog
        thingName={'infrastructure service'}
        uiComponent={<DeleteIconButton />}
        onDelete={() =>
          delService({
            repositoryId: Number.parseInt(params.repoId),
            serviceName: row.original.name,
          })
        }
      />
    </div>
  );
};

const InfrastructureServices = () => {
  const params = Route.useParams();
  const search = Route.useSearch();
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : defaultPageSize;

  const {
    data: repository,
    error: repositoryError,
    isPending: repositoryIsPending,
    isError: repositoryIsError,
  } = useRepositoriesServiceGetApiV1RepositoriesByRepositoryId({
    repositoryId: Number.parseInt(params.repoId),
  });

  const {
    data: infraServices,
    error: infraError,
    isPending: infraIsPending,
    isError: infraIsError,
  } = useRepositoriesServiceGetApiV1RepositoriesByRepositoryIdInfrastructureServices(
    {
      repositoryId: Number.parseInt(params.repoId),
      limit: pageSize,
      offset: pageIndex * pageSize,
    }
  );

  const columns: ColumnDef<InfrastructureService>[] = [
    {
      accessorKey: 'details',
      header: undefined,
      cell: ({ row }) => (
        <div className='flex flex-col'>
          <div>{row.original.name}</div>
          <div className='text-muted-foreground text-sm'>
            {row.original.description}
          </div>
          <div>{row.original.url}</div>
        </div>
      ),
      enableColumnFilter: false,
    },
    {
      accessorKey: 'usernameSecretRef',
      header: 'Username Secret',
      cell: ({ row }) => (
        <div className='flex items-baseline'>
          {row.original.usernameSecretRef}
        </div>
      ),
      enableColumnFilter: false,
    },
    {
      accessorKey: 'passwordSecretRef',
      header: 'Password Secret',
      cell: ({ row }) => (
        <div className='flex items-baseline'>
          {row.original.passwordSecretRef}
        </div>
      ),
      enableColumnFilter: false,
    },
    {
      accessorKey: 'credentialsTypes',
      header: 'Credentials Included In Files',
      cell: ({ row }) => {
        const inFiles = row.original.credentialsTypes?.map((type) => {
          if (type === 'NETRC_FILE') return 'Netrc File';
          if (type === 'GIT_CREDENTIALS_FILE') return 'Git Credentials File';
        });

        return inFiles?.join(', ');
      },
      enableColumnFilter: false,
    },
    {
      id: 'actions',
      cell: ActionCell,
      enableColumnFilter: false,
    },
  ];

  const table = useReactTable({
    data: infraServices?.data || [],
    columns,
    pageCount: Math.ceil(
      (infraServices?.pagination.totalCount ?? 0) / pageSize
    ),
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
    },
    getCoreRowModel: getCoreRowModel(),
    manualPagination: true,
  });

  if (repositoryIsPending || infraIsPending) {
    return <LoadingIndicator />;
  }

  if (repositoryIsError || infraIsError) {
    toast.error('Unable to load data', {
      description: <ToastError error={repositoryError || infraError} />,
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
        <CardTitle>Infrastructure Services</CardTitle>
        <CardDescription>
          Manage infrastructure services for {repository.url}
        </CardDescription>
        <div className='py-2'>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button asChild size='sm' className='ml-auto gap-1'>
                <Link
                  to='/organizations/$orgId/products/$productId/repositories/$repoId/infrastructure-services/create'
                  params={{
                    orgId: params.orgId,
                    productId: params.productId,
                    repoId: params.repoId,
                  }}
                >
                  New infrastructure service
                  <PlusIcon className='h-4 w-4' />
                </Link>
              </Button>
            </TooltipTrigger>
            <TooltipContent>
              Create a new infrastructure service for this repository.
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
  '/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/infrastructure-services/'
)({
  validateSearch: paginationSearchParameterSchema,
  loaderDeps: ({ search: { page, pageSize } }) => ({ page, pageSize }),
  loader: async ({ context, params, deps: { page, pageSize } }) => {
    await Promise.allSettled([
      prefetchUseRepositoriesServiceGetApiV1RepositoriesByRepositoryId(
        context.queryClient,
        {
          repositoryId: Number.parseInt(params.repoId),
        }
      ),
      prefetchUseRepositoriesServiceGetApiV1RepositoriesByRepositoryIdInfrastructureServices(
        context.queryClient,
        {
          repositoryId: Number.parseInt(params.repoId),
          limit: pageSize || defaultPageSize,
          offset: page ? (page - 1) * (pageSize || defaultPageSize) : 0,
        }
      ),
    ]);
  },
  component: InfrastructureServices,
  pendingComponent: LoadingIndicator,
});
