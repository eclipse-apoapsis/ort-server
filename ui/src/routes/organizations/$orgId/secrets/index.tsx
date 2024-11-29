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
import { useState } from 'react';

import {
  useOrganizationsServiceGetOrganizationById,
  useSecretsServiceDeleteSecretByOrganizationIdAndName,
  useSecretsServiceGetSecretsByOrganizationId,
  useSecretsServiceGetSecretsByOrganizationIdKey,
} from '@/api/queries';
import {
  prefetchUseOrganizationsServiceGetOrganizationById,
  prefetchUseSecretsServiceGetSecretsByOrganizationId,
} from '@/api/queries/prefetch';
import { useOrganizationsServiceGetOrganizationByIdSuspense } from '@/api/queries/suspense';
import { ApiError, Secret } from '@/api/requests';
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

const ActionCell = ({ row }: CellContext<Secret, unknown>) => {
  const params = Route.useParams();
  const queryClient = useQueryClient();
  const [openDelDialog, setOpenDelDialog] = useState(false);

  const { data: organization } =
    useOrganizationsServiceGetOrganizationByIdSuspense({
      organizationId: Number.parseInt(params.orgId),
    });

  const { mutateAsync: delSecret, isPending: delIsPending } =
    useSecretsServiceDeleteSecretByOrganizationIdAndName({
      onSuccess() {
        setOpenDelDialog(false);
        toast.info('Delete Secret', {
          description: `Secret "${row.original.name}" deleted successfully.`,
        });
        queryClient.invalidateQueries({
          queryKey: [
            useSecretsServiceGetSecretsByOrganizationIdKey,
            params.orgId,
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
    });

  return (
    <div className='flex justify-end gap-1'>
      <Tooltip>
        <TooltipTrigger asChild>
          <Link
            to='/organizations/$orgId/secrets/$secretName/edit'
            params={{ orgId: params.orgId, secretName: row.original.name }}
            className={cn(buttonVariants({ variant: 'outline' }), 'h-9 px-2')}
          >
            <span className='sr-only'>Edit</span>
            <EditIcon size={16} />
          </Link>
        </TooltipTrigger>
        <TooltipContent>Edit this secret</TooltipContent>
      </Tooltip>
      <DeleteDialog
        open={openDelDialog}
        setOpen={setOpenDelDialog}
        description={
          <>
            Are you sure you want to delete the secret{' '}
            <span className='font-bold'>{row.original.name}</span>?
          </>
        }
        onDelete={() =>
          delSecret({
            organizationId: organization.id,
            secretName: row.original.name,
          })
        }
        isPending={delIsPending}
        trigger={<DeleteIconButton />}
      />
    </div>
  );
};

const columns: ColumnDef<Secret>[] = [
  {
    accessorKey: 'name',
    header: 'Name',
  },
  {
    accessorKey: 'description',
    header: 'Description',
  },
  {
    id: 'actions',
    cell: ActionCell,
  },
];

const OrganizationSecrets = () => {
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
    data: secrets,
    error: secretsError,
    isPending: secretsIsPending,
    isError: secretsIsError,
  } = useSecretsServiceGetSecretsByOrganizationId({
    organizationId: Number.parseInt(params.orgId),
    limit: pageSize,
    offset: pageIndex * pageSize,
  });

  const table = useReactTable({
    data: secrets?.data || [],
    columns,
    pageCount: Math.ceil((secrets?.pagination.totalCount ?? 0) / pageSize),
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
    },
    getCoreRowModel: getCoreRowModel(),
    manualPagination: true,
  });

  if (orgIsPending || secretsIsPending) {
    return <LoadingIndicator />;
  }

  if (orgIsError || secretsIsError) {
    toast.error('Unable to load data', {
      description: <ToastError error={orgError || secretsError} />,
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
        <CardTitle>Secrets</CardTitle>
        <CardDescription>
          Manage secrets for {organization.name}
        </CardDescription>
        <div className='py-2'>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button asChild size='sm' className='ml-auto gap-1'>
                <Link
                  to='/organizations/$orgId/secrets/create-secret'
                  params={{ orgId: params.orgId }}
                >
                  New secret
                  <PlusIcon className='h-4 w-4' />
                </Link>
              </Button>
            </TooltipTrigger>
            <TooltipContent>
              Create a new secret for this organization
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

export const Route = createFileRoute('/organizations/$orgId/secrets/')({
  validateSearch: paginationSearchParameterSchema,
  loaderDeps: ({ search: { page, pageSize } }) => ({ page, pageSize }),
  loader: async ({ context, params, deps: { page, pageSize } }) => {
    await Promise.allSettled([
      prefetchUseOrganizationsServiceGetOrganizationById(context.queryClient, {
        organizationId: Number.parseInt(params.orgId),
      }),
      prefetchUseSecretsServiceGetSecretsByOrganizationId(context.queryClient, {
        organizationId: Number.parseInt(params.orgId),
        limit: pageSize || defaultPageSize,
        offset: page ? (page - 1) * (pageSize || defaultPageSize) : 0,
      }),
    ]);
  },
  component: OrganizationSecrets,
  pendingComponent: LoadingIndicator,
});
