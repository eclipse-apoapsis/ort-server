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
import { EditIcon, PlusIcon } from 'lucide-react';

import {
  useProductsServiceDeleteProductById,
  useProductsServiceGetProductById,
  useRepositoriesServiceGetRepositoriesByProductId,
} from '@/api/queries';
import {
  prefetchUseProductsServiceGetProductById,
  prefetchUseRepositoriesServiceGetRepositoriesByProductId,
} from '@/api/queries/prefetch';
import { ApiError, Repository } from '@/api/requests';
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
import { toast } from '@/lib/toast';
import { paginationSchema } from '@/schemas';

const defaultPageSize = 10;

const columns: ColumnDef<Repository>[] = [
  {
    accessorKey: 'repository',
    header: () => <div>Repositories</div>,
    cell: ({ row }) => (
      <>
        <Link
          className='block font-semibold text-blue-400 hover:underline'
          to={'/organizations/$orgId/products/$productId/repositories/$repoId'}
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
  },
];

const ProductComponent = () => {
  const params = Route.useParams();
  const navigate = Route.useNavigate();
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

  const { mutateAsync: deleteProduct, isPending } =
    useProductsServiceDeleteProductById({
      onSuccess() {
        toast.info('Delete Product', {
          description: `Product "${product?.name}" deleted successfully.`,
        });
        navigate({
          to: '/organizations/$orgId',
          params: { orgId: params.orgId },
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
    await deleteProduct({
      productId: Number.parseInt(params.productId),
    });
  }

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
    <TooltipProvider>
      <Card>
        <CardHeader>
          <CardTitle className='flex flex-row justify-between'>
            <div className='flex items-stretch'>
              <div className='flex items-center pb-1'>{product.name}</div>
              <Tooltip>
                <TooltipTrigger>
                  <Button
                    asChild
                    size='sm'
                    variant='outline'
                    className='ml-2 px-2'
                  >
                    <Link
                      to='/organizations/$orgId/products/$productId/edit'
                      params={{
                        orgId: params.orgId,
                        productId: params.productId,
                      }}
                    >
                      <EditIcon className='h-4 w-4' />
                    </Link>
                  </Button>
                </TooltipTrigger>
                <TooltipContent>Edit this product</TooltipContent>
              </Tooltip>
            </div>
            <DeleteDialog
              item={{
                descriptor: 'product',
                name: product.name,
              }}
              onDelete={handleDelete}
              isPending={isPending}
            />
          </CardTitle>
          <CardDescription>{product.description}</CardDescription>
          <div className='py-2'>
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
                ...Route,
                search: { ...search, page: currentPage },
              };
            }}
            setPageSizeOptions={(size) => {
              return {
                ...Route,
                search: { ...search, pageSize: size },
              };
            }}
          />
        </CardContent>
      </Card>
    </TooltipProvider>
  );
};

export const Route = createFileRoute(
  '/_layout/organizations/$orgId/products/$productId/'
)({
  validateSearch: paginationSchema,
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
  component: ProductComponent,
  pendingComponent: LoadingIndicator,
});
