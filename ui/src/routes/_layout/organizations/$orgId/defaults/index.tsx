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

import {
  useQueryClient,
  useSuspenseQueries,
  useSuspenseQuery,
} from '@tanstack/react-query';
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
  useOrganizationsServiceGetOrganizationByIdKey,
  useSecretsServiceDeleteSecretByOrganizationIdAndName,
  useSecretsServiceGetSecretsByOrganizationId,
} from '@/api/queries';
import {
  ApiError,
  OrganizationsService,
  Secret,
  SecretsService,
} from '@/api/requests';
import { DataTable } from '@/components/data-table/data-table';
import { DeleteDialog } from '@/components/delete-dialog';
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
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { useToast } from '@/components/ui/use-toast';
import { decodePropertyPath, isDefault } from '@/helpers/defaults-helpers';
import { cn } from '@/lib/utils';
import { paginationSchema } from '@/schemas';

const defaultPageSize = 10;

const ActionCell = ({ row }: CellContext<Secret, unknown>) => {
  const params = Route.useParams();
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [openDelDialog, setOpenDelDialog] = useState(false);

  const { data: organization } = useSuspenseQuery({
    queryKey: [useOrganizationsServiceGetOrganizationByIdKey, params.orgId],
    queryFn: async () =>
      await OrganizationsService.getOrganizationById({
        organizationId: Number.parseInt(params.orgId),
      }),
  });

  const { mutateAsync: delSecret, isPending: delIsPending } =
    useSecretsServiceDeleteSecretByOrganizationIdAndName({
      onSuccess() {
        setOpenDelDialog(false);
        toast({
          title: 'Delete Default',
          description: `Default value "${decodePropertyPath(row.original.name)}" deleted successfully.`,
        });
        queryClient.invalidateQueries({
          queryKey: [useSecretsServiceGetSecretsByOrganizationId],
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

  return (
    <div className='flex justify-end gap-1'>
      <TooltipProvider>
        <Tooltip>
          <TooltipTrigger asChild>
            <Link
              to='/organizations/$orgId/defaults/$defaultName/edit'
              params={{ orgId: params.orgId, defaultName: row.original.name }}
              className={cn(buttonVariants({ variant: 'outline' }), 'h-9 px-2')}
            >
              <span className='sr-only'>Edit</span>
              <EditIcon size={16} />
            </Link>
          </TooltipTrigger>
          <TooltipContent>Edit this default run property</TooltipContent>
        </Tooltip>
      </TooltipProvider>
      <DeleteDialog
        open={openDelDialog}
        setOpen={setOpenDelDialog}
        item={{
          descriptor: 'default run property',
          name: decodePropertyPath(row.original.name),
        }}
        onDelete={() =>
          delSecret({
            organizationId: organization.id,
            secretName: row.original.name,
          })
        }
        isPending={delIsPending}
      />
    </div>
  );
};

const columns: ColumnDef<Secret>[] = [
  {
    accessorKey: 'name',
    header: 'Run property',
    // Do not show the property encoding prefix in the table
    cell: ({ row }) => decodePropertyPath(row.original.name),
  },
  {
    accessorKey: 'description',
    header: 'Value',
  },
  {
    id: 'actions',
    cell: ActionCell,
  },
];

const OrganizationDefaults = () => {
  const params = Route.useParams();
  const search = Route.useSearch();
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : defaultPageSize;

  const [{ data: organization }, { data: secrets }] = useSuspenseQueries({
    queries: [
      {
        queryKey: [useOrganizationsServiceGetOrganizationByIdKey, params.orgId],
        queryFn: async () =>
          await OrganizationsService.getOrganizationById({
            organizationId: Number.parseInt(params.orgId),
          }),
      },
      {
        queryKey: [
          useSecretsServiceGetSecretsByOrganizationId,
          params.orgId,
          pageIndex,
          pageSize,
        ],
        queryFn: async () =>
          await SecretsService.getSecretsByOrganizationId({
            organizationId: Number.parseInt(params.orgId),
            limit: pageSize,
            offset: pageIndex * pageSize,
          }),
      },
    ],
  });

  // Only show secrets which are encoded as default properties
  const tableData = secrets?.data.filter((secret) => isDefault(secret.name));

  const table = useReactTable({
    data: tableData || [],
    columns,
    pageCount: Math.ceil(secrets.pagination.totalCount / pageSize),
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
          <CardTitle>Default Run Properties</CardTitle>
          <CardDescription>
            Manage default run properties for {organization.name}
          </CardDescription>
          <div className='py-2'>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button asChild size='sm' className='ml-auto gap-1'>
                  <Link
                    to='/organizations/$orgId/defaults/create-default'
                    params={{ orgId: params.orgId }}
                  >
                    New default property
                    <PlusIcon className='h-4 w-4' />
                  </Link>
                </Button>
              </TooltipTrigger>
              <TooltipContent>
                Create a new default run property for this organization
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

export const Route = createFileRoute('/_layout/organizations/$orgId/defaults/')(
  {
    validateSearch: paginationSchema,
    loaderDeps: ({ search: { page, pageSize } }) => ({ page, pageSize }),
    loader: async ({ context, params, deps: { page, pageSize } }) => {
      await Promise.allSettled([
        context.queryClient.ensureQueryData({
          queryKey: [
            useOrganizationsServiceGetOrganizationByIdKey,
            params.orgId,
          ],
          queryFn: () =>
            OrganizationsService.getOrganizationById({
              organizationId: Number.parseInt(params.orgId),
            }),
        }),
        context.queryClient.ensureQueryData({
          queryKey: [
            useSecretsServiceGetSecretsByOrganizationId,
            params.orgId,
            page,
            pageSize,
          ],
          queryFn: () =>
            SecretsService.getSecretsByOrganizationId({
              organizationId: Number.parseInt(params.orgId),
              limit: pageSize || defaultPageSize,
              offset: page ? (page - 1) * (pageSize || defaultPageSize) : 0,
            }),
        }),
      ]);
    },
    component: OrganizationDefaults,
    pendingComponent: LoadingIndicator,
  }
);
