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
import { EditIcon, OctagonAlert, PlusIcon, TrashIcon } from 'lucide-react';

import {
  useProductsServiceDeleteProductById,
  useProductsServiceGetProductByIdKey,
  useRepositoriesServiceGetRepositoriesByProductIdKey,
} from '@/api/queries';
import {
  ApiError,
  ProductsService,
  RepositoriesService,
  Repository,
} from '@/api/requests';
import { DataTable } from '@/components/data-table/data-table';
import { LoadingIndicator } from '@/components/loading-indicator';
import { ToastError } from '@/components/toast-error';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog';
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
  const navigate = useNavigate();
  const { toast } = useToast();
  const search = Route.useSearch();
  const pageIndex = search.page ? search.page - 1 : 0;
  const pageSize = search.pageSize ? search.pageSize : defaultPageSize;

  const [{ data: product }, { data: repositories }] = useSuspenseQueries({
    queries: [
      {
        queryKey: [useProductsServiceGetProductByIdKey, params.productId],
        queryFn: async () =>
          await ProductsService.getProductById({
            productId: Number.parseInt(params.productId),
          }),
      },
      {
        queryKey: [
          useRepositoriesServiceGetRepositoriesByProductIdKey,
          params.productId,
          pageIndex,
          pageSize,
        ],
        queryFn: async () =>
          await RepositoriesService.getRepositoriesByProductId({
            productId: Number.parseInt(params.productId),
            limit: pageSize,
            offset: pageIndex * pageSize,
          }),
      },
    ],
  });

  const { mutateAsync: deleteProduct } = useProductsServiceDeleteProductById({
    onSuccess() {
      toast({
        title: 'Delete Product',
        description: `Product "${product.name}" deleted successfully.`,
      });
      navigate({
        to: '/organizations/$orgId',
        params: { orgId: params.orgId },
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
    await deleteProduct({
      productId: Number.parseInt(params.productId),
    });
  }

  const table = useReactTable({
    data: repositories?.data || [],
    columns,
    pageCount: Math.ceil(repositories.pagination.totalCount / pageSize),
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
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button size='sm' variant='outline' className='px-2'>
                  <TrashIcon className='h-4 w-4' />
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <div className='flex items-center'>
                    <OctagonAlert className='h-8 w-8 pr-2 text-red-500' />
                    <AlertDialogTitle>Delete product</AlertDialogTitle>
                  </div>
                </AlertDialogHeader>
                <AlertDialogDescription>
                  Are you sure you want to delete this product:{' '}
                  <span className='font-bold'>{product.name}</span>?
                </AlertDialogDescription>
                <AlertDialogFooter>
                  <AlertDialogCancel>Cancel</AlertDialogCancel>
                  <AlertDialogAction
                    onClick={handleDelete}
                    className='bg-red-500'
                  >
                    Delete
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
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
                    New repository
                    <PlusIcon className='h-4 w-4' />
                  </Link>
                </Button>
              </TooltipTrigger>
              <TooltipContent>
                Add a new repository for this product
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
  '/_layout/organizations/$orgId/products/$productId/'
)({
  validateSearch: paginationSchema,
  loaderDeps: ({ search: { page, pageSize } }) => ({ page, pageSize }),
  loader: async ({ context, params, deps: { page, pageSize } }) => {
    await Promise.allSettled([
      context.queryClient.ensureQueryData({
        queryKey: [useProductsServiceGetProductByIdKey, params.productId],
        queryFn: () =>
          ProductsService.getProductById({
            productId: Number.parseInt(params.productId),
          }),
      }),
      context.queryClient.ensureQueryData({
        queryKey: [
          useRepositoriesServiceGetRepositoriesByProductIdKey,
          params.productId,
          page,
          pageSize,
        ],
        queryFn: () =>
          RepositoriesService.getRepositoriesByProductId({
            productId: Number.parseInt(params.productId),
            limit: pageSize || defaultPageSize,
            offset: page ? (page - 1) * (pageSize || defaultPageSize) : 0,
          }),
      }),
    ]);
  },
  component: ProductComponent,
  pendingComponent: LoadingIndicator,
});
