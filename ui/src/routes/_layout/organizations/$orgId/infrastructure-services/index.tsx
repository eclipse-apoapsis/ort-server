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
import { EditIcon, Pencil, PlusIcon } from 'lucide-react';
import { useState } from 'react';

import {
  useInfrastructureServicesServiceDeleteInfrastructureServiceForOrganizationIdAndName,
  useInfrastructureServicesServiceGetInfrastructureServicesByOrganizationId,
  useInfrastructureServicesServiceGetInfrastructureServicesByOrganizationIdKey,
  useOrganizationsServiceGetOrganizationById,
} from '@/api/queries';
import {
  prefetchUseInfrastructureServicesServiceGetInfrastructureServicesByOrganizationId,
  prefetchUseOrganizationsServiceGetOrganizationById,
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
  const [openDelDialog, setOpenDelDialog] = useState(false);

  const { mutateAsync: delService, isPending: delIsPending } =
    useInfrastructureServicesServiceDeleteInfrastructureServiceForOrganizationIdAndName(
      {
        onSuccess() {
          setOpenDelDialog(false);
          toast.info('Delete Infrastructure Service', {
            description: `Infrastructure service "${row.original.name}" deleted successfully.`,
          });
          queryClient.invalidateQueries({
            queryKey: [
              useInfrastructureServicesServiceGetInfrastructureServicesByOrganizationIdKey,
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
            to='/organizations/$orgId/infrastructure-services/$serviceName/edit'
            params={{ orgId: params.orgId, serviceName: row.original.name }}
            className={cn(buttonVariants({ variant: 'outline' }), 'h-9 px-2')}
          >
            <span className='sr-only'>Edit</span>
            <EditIcon size={16} />
          </Link>
        </TooltipTrigger>
        <TooltipContent>Edit this infrastructure service</TooltipContent>
      </Tooltip>

      <DeleteDialog
        open={openDelDialog}
        setOpen={setOpenDelDialog}
        item={{ descriptor: 'infrastructure service', name: row.original.name }}
        onDelete={() =>
          delService({
            organizationId: Number.parseInt(params.orgId),
            serviceName: row.original.name,
          })
        }
        isPending={delIsPending}
        trigger={<DeleteIconButton />}
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
    data: organization,
    error: orgError,
    isPending: orgIsPending,
    isError: orgIsError,
  } = useOrganizationsServiceGetOrganizationById({
    organizationId: Number.parseInt(params.orgId),
  });

  const {
    data: infraServices,
    error: infraError,
    isPending: infraIsPending,
    isError: infraIsError,
  } = useInfrastructureServicesServiceGetInfrastructureServicesByOrganizationId(
    {
      organizationId: Number.parseInt(params.orgId),
      limit: pageSize,
      offset: pageIndex * pageSize,
    }
  );

  const columns: ColumnDef<InfrastructureService>[] = [
    {
      accessorKey: 'name',
      header: 'Name',
    },
    {
      accessorKey: 'description',
      header: 'Description',
    },
    {
      accessorKey: 'url',
      header: 'URL',
    },
    {
      accessorKey: 'usernameSecretRef',
      header: 'Username Secret',
      cell: ({ row }) => (
        <div className='flex items-baseline'>
          {row.original.usernameSecretRef}{' '}
          <Tooltip>
            <TooltipTrigger asChild>
              <Link
                to='/organizations/$orgId/secrets/$secretName/edit'
                params={{
                  orgId: params.orgId,
                  secretName: row.original.usernameSecretRef,
                }}
                search={{
                  returnTo: '/organizations/$orgId/infrastructure-services',
                }}
                className='px-2'
              >
                <span className='sr-only'>Edit</span>
                <Pencil size={16} className='inline' />
              </Link>
            </TooltipTrigger>
            <TooltipContent>
              Edit the secret "{row.original.usernameSecretRef}"
            </TooltipContent>
          </Tooltip>
        </div>
      ),
    },
    {
      accessorKey: 'passwordSecretRef',
      header: 'Password Secret',
      cell: ({ row }) => (
        <div className='flex items-baseline'>
          {row.original.passwordSecretRef}{' '}
          <Tooltip>
            <TooltipTrigger asChild>
              <Link
                to='/organizations/$orgId/secrets/$secretName/edit'
                params={{
                  orgId: params.orgId,
                  secretName: row.original.passwordSecretRef,
                }}
                search={{
                  returnTo: '/organizations/$orgId/infrastructure-services',
                }}
                className='px-2'
              >
                <span className='sr-only'>Edit</span>
                <Pencil size={16} className='inline' />
              </Link>
            </TooltipTrigger>
            <TooltipContent>
              Edit the secret "{row.original.passwordSecretRef}"
            </TooltipContent>
          </Tooltip>
        </div>
      ),
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
    },
    {
      id: 'actions',
      cell: ActionCell,
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

  if (orgIsPending || infraIsPending) {
    return <LoadingIndicator />;
  }

  if (orgIsError || infraIsError) {
    toast.error('Unable to load data', {
      description: <ToastError error={orgError || infraError} />,
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
          Manage infrastructure services for {organization.name}
        </CardDescription>
        <div className='py-2'>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button asChild size='sm' className='ml-auto gap-1'>
                <Link
                  to='/organizations/$orgId/infrastructure-services/create'
                  params={{ orgId: params.orgId }}
                >
                  New infrastructure service
                  <PlusIcon className='h-4 w-4' />
                </Link>
              </Button>
            </TooltipTrigger>
            <TooltipContent>
              Create a new infrastructure service for this organization
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
  '/_layout/organizations/$orgId/infrastructure-services/'
)({
  validateSearch: paginationSearchParameterSchema,
  loaderDeps: ({ search: { page, pageSize } }) => ({ page, pageSize }),
  loader: async ({ context, params, deps: { page, pageSize } }) => {
    await Promise.allSettled([
      prefetchUseOrganizationsServiceGetOrganizationById(context.queryClient, {
        organizationId: Number.parseInt(params.orgId),
      }),
      prefetchUseInfrastructureServicesServiceGetInfrastructureServicesByOrganizationId(
        context.queryClient,
        {
          organizationId: Number.parseInt(params.orgId),
          limit: pageSize || defaultPageSize,
          offset: page ? (page - 1) * (pageSize || defaultPageSize) : 0,
        }
      ),
    ]);
  },
  component: InfrastructureServices,
  pendingComponent: LoadingIndicator,
});
