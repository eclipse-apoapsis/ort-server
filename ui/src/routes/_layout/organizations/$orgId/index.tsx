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
import { EditIcon, Loader2, PlusIcon } from 'lucide-react';

import {
  useOrganizationsServiceDeleteOrganizationById,
  useOrganizationsServiceGetOrganizationById,
  useProductsServiceGetOrganizationProducts,
  useRepositoriesServiceGetRepositoriesByProductId,
} from '@/api/queries';
import {
  prefetchUseOrganizationsServiceGetOrganizationById,
  prefetchUseProductsServiceGetOrganizationProducts,
} from '@/api/queries/prefetch';
import { ApiError, Product } from '@/api/requests';
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
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { toast } from '@/lib/toast';
import { paginationSchema } from '@/schemas';
import { LastJobStatus } from './products/$productId/-components/last-job-status';
import { LastRunDate } from './products/$productId/-components/last-run-date';
import { LastRunStatus } from './products/$productId/-components/last-run-status';

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
  {
    accessorKey: 'runStatus',
    header: () => <div>Last Run Status</div>,
    cell: function CellComponent({ row }) {
      const { data, isPending, isError } =
        useRepositoriesServiceGetRepositoriesByProductId({
          productId: row.original.id,
          limit: 1,
        });

      if (isPending)
        return (
          <>
            <span className='sr-only'>Loading...</span>
            <Loader2 size={16} className='mx-3 animate-spin' />
          </>
        );

      if (isError) return <span>Error loading data.</span>;

      if (data.pagination.totalCount === 1)
        return <LastRunStatus repoId={data.data[0].id} />;
      else
        return <span>Contains {data.pagination.totalCount} repositories</span>;
    },
  },
  {
    accessorKey: 'lastRunDate',
    header: () => <div>Last Run Date</div>,
    cell: function CellComponent({ row }) {
      const { data, isPending, isError } =
        useRepositoriesServiceGetRepositoriesByProductId({
          productId: row.original.id,
          limit: 1,
        });

      if (isPending)
        return (
          <>
            <span className='sr-only'>Loading...</span>
            <Loader2 size={16} className='mx-3 animate-spin' />
          </>
        );

      if (isError) return <span>Error loading data.</span>;

      if (data.pagination.totalCount === 1)
        return <LastRunDate repoId={data.data[0].id} />;
      else return null;
    },
  },
  {
    accessorKey: 'jobStatus',
    header: () => <div>Last Job Status</div>,
    cell: function CellComponent({ row }) {
      const { data, isPending, isError } =
        useRepositoriesServiceGetRepositoriesByProductId({
          productId: row.original.id,
          limit: 1,
        });

      if (isPending)
        return (
          <>
            <span className='sr-only'>Loading...</span>
            <Loader2 size={16} className='mx-3 animate-spin' />
          </>
        );

      if (isError) return <span>Error loading data.</span>;

      if (data.pagination.totalCount === 1)
        return <LastJobStatus repoId={data.data[0].id} />;
      else return null;
    },
  },
];

const OrganizationComponent = () => {
  const params = Route.useParams();
  const navigate = Route.useNavigate();
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
    data: products,
    error: prodError,
    isPending: prodIsPending,
    isError: prodIsError,
  } = useProductsServiceGetOrganizationProducts({
    organizationId: Number.parseInt(params.orgId),
    limit: pageSize,
    offset: pageIndex * pageSize,
  });

  const { mutateAsync: deleteOrganization, isPending } =
    useOrganizationsServiceDeleteOrganizationById({
      onSuccess() {
        toast.info('Delete Organization', {
          description: `Organization "${organization?.name}" deleted successfully.`,
        });
        navigate({
          to: '/',
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
    await deleteOrganization({
      organizationId: Number.parseInt(params.orgId),
    });
  }

  const table = useReactTable({
    data: products?.data || [],
    columns,
    pageCount: Math.ceil((products?.pagination.totalCount ?? 0) / pageSize),
    state: {
      pagination: {
        pageIndex,
        pageSize,
      },
    },
    getCoreRowModel: getCoreRowModel(),
    manualPagination: true,
  });

  if (orgIsPending || prodIsPending) {
    return <LoadingIndicator />;
  }

  if (orgIsError || prodIsError) {
    toast.error('Unable to load data', {
      description: <ToastError error={orgError || prodError} />,
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
                  Add product
                  <PlusIcon className='h-4 w-4' />
                </Link>
              </Button>
            </TooltipTrigger>
            <TooltipContent>
              Add a product for managing repositories
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
              search: { ...search, pageSize: size },
            };
          }}
        />
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute('/_layout/organizations/$orgId/')({
  validateSearch: paginationSchema,
  loaderDeps: ({ search: { page, pageSize } }) => ({ page, pageSize }),
  loader: async ({ context, params, deps: { page, pageSize } }) => {
    await Promise.allSettled([
      prefetchUseOrganizationsServiceGetOrganizationById(context.queryClient, {
        organizationId: Number.parseInt(params.orgId),
      }),
      prefetchUseProductsServiceGetOrganizationProducts(context.queryClient, {
        organizationId: Number.parseInt(params.orgId),
        limit: pageSize || defaultPageSize,
        offset: page ? (page - 1) * (pageSize || defaultPageSize) : 0,
      }),
    ]);
  },
  component: OrganizationComponent,
  pendingComponent: LoadingIndicator,
});
