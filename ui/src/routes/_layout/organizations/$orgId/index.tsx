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
import { EditIcon, PlusIcon } from 'lucide-react';

import {
  useOrganizationsServiceDeleteOrganizationById,
  useOrganizationsServiceGetOrganizationByIdKey,
  useProductsServiceGetOrganizationProductsKey,
} from '@/api/queries';
import {
  ApiError,
  OrganizationsService,
  Product,
  ProductsService,
} from '@/api/requests';
import { DataTable } from '@/components/data-table/data-table';
import { DeleteDialog } from '@/components/delete-dialog';
import { LoadingIndicator } from '@/components/loading-indicator';
import { ToastError } from '@/components/toast-error';
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
import { paginationSchema } from '@/schemas';

const defaultPageSize = 10;

const columns: ColumnDef<Product>[] = [
  {
    accessorKey: 'product',
    header: () => <div>Products</div>,
    cell: ({ row }) => (
      <>
        <Link
          className='block font-semibold text-blue-400 hover:underline'
          to={`/organizations/$orgId/products/$productId`}
          params={{
            orgId: row.original.organizationId.toString(),
            productId: row.original.id.toString(),
          }}
        >
          {row.original.name}
        </Link>
        <div className='text-sm text-muted-foreground md:inline'>
          {row.original.description}
        </div>
      </>
    ),
  },
];

const OrganizationComponent = () => {
  const params = Route.useParams();
  const navigate = useNavigate();
  const { toast } = useToast();
  const search = Route.useSearch();
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : defaultPageSize;

  const [{ data: organization }, { data: products }] = useSuspenseQueries({
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
          useProductsServiceGetOrganizationProductsKey,
          params.orgId,
          pageIndex,
          pageSize,
        ],
        queryFn: async () =>
          await ProductsService.getOrganizationProducts({
            organizationId: Number.parseInt(params.orgId),
            limit: pageSize,
            offset: pageIndex * pageSize,
          }),
      },
    ],
  });

  const { mutateAsync: deleteOrganization, isPending } =
    useOrganizationsServiceDeleteOrganizationById({
      onSuccess() {
        toast({
          title: 'Delete Organization',
          description: `Organization "${organization.name}" deleted successfully.`,
        });
        navigate({
          to: '/',
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
    await deleteOrganization({
      organizationId: Number.parseInt(params.orgId),
    });
  }

  const table = useReactTable({
    data: products?.data || [],
    columns,
    pageCount: Math.ceil(products.pagination.totalCount / pageSize),
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
              <div className='flex items-center pb-1'>{organization.name}</div>
              <Tooltip>
                <TooltipTrigger>
                  <Button
                    asChild
                    size='sm'
                    variant='outline'
                    className='ml-2 px-2'
                  >
                    <Link
                      to='/organizations/$orgId/edit'
                      params={{ orgId: organization.id.toString() }}
                    >
                      <EditIcon className='h-4 w-4' />
                    </Link>
                  </Button>
                </TooltipTrigger>
                <TooltipContent>Edit this organization</TooltipContent>
              </Tooltip>
            </div>
            <DeleteDialog
              item={{
                descriptor: 'organization',
                name: organization.name,
              }}
              onDelete={handleDelete}
              isPending={isPending}
            />
          </CardTitle>
          <CardDescription>{organization.description}</CardDescription>
          <div className='py-2'>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button asChild size='sm' className='ml-auto gap-1'>
                  <Link
                    to='/organizations/$orgId/create-product'
                    params={{ orgId: organization.id.toString() }}
                  >
                    New product
                    <PlusIcon className='h-4 w-4' />
                  </Link>
                </Button>
              </TooltipTrigger>
              <TooltipContent>
                Create a new product for this organization
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

export const Route = createFileRoute('/_layout/organizations/$orgId/')({
  validateSearch: paginationSchema,
  loaderDeps: ({ search: { page, pageSize } }) => ({ page, pageSize }),
  loader: async ({ context, params, deps: { page, pageSize } }) => {
    await Promise.allSettled([
      context.queryClient.ensureQueryData({
        queryKey: [useOrganizationsServiceGetOrganizationByIdKey, params.orgId],
        queryFn: () =>
          OrganizationsService.getOrganizationById({
            organizationId: Number.parseInt(params.orgId),
          }),
      }),
      context.queryClient.ensureQueryData({
        queryKey: [
          useProductsServiceGetOrganizationProductsKey,
          params.orgId,
          page,
          pageSize,
        ],
        queryFn: () =>
          ProductsService.getOrganizationProducts({
            organizationId: Number.parseInt(params.orgId),
            limit: pageSize || defaultPageSize,
            offset: page ? (page - 1) * (pageSize || defaultPageSize) : 0,
          }),
      }),
    ]);
  },
  component: OrganizationComponent,
  pendingComponent: LoadingIndicator,
});
