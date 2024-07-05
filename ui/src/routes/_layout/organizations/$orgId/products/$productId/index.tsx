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
import { EditIcon, OctagonAlert, PlusIcon, TrashIcon } from 'lucide-react';

import {
  useProductsServiceDeleteProductById,
  useProductsServiceGetProductByIdKey,
  useRepositoriesServiceGetRepositoriesByProductIdKey,
} from '@/api/queries';
import { ApiError, ProductsService, RepositoriesService } from '@/api/requests';
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
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { useToast } from '@/components/ui/use-toast';

const ProductComponent = () => {
  const params = Route.useParams();
  const navigate = useNavigate();
  const { toast } = useToast();

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
        ],
        queryFn: async () =>
          await RepositoriesService.getRepositoriesByProductId({
            productId: Number.parseInt(params.productId),
            limit: 1000,
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
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className='flex flex-row items-center justify-between pb-1.5 pr-0'>
                  Repositories
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {repositories?.data.map((repo) => {
                return (
                  <TableRow key={repo.id}>
                    <TableCell>
                      <div>
                        <Link
                          to={
                            '/organizations/$orgId/products/$productId/repositories/$repoId'
                          }
                          params={{
                            orgId: params.orgId,
                            productId: params.productId,
                            repoId: repo.id.toString(),
                          }}
                          className='font-semibold text-blue-400 hover:underline'
                        >
                          {repo.url}
                        </Link>
                      </div>
                      <div className='hidden text-sm text-muted-foreground md:inline'>
                        {repo.type}
                      </div>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </TooltipProvider>
  );
};

export const Route = createFileRoute(
  '/_layout/organizations/$orgId/products/$productId/'
)({
  loader: async ({ context, params }) => {
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
        ],
        queryFn: () =>
          RepositoriesService.getRepositoriesByProductId({
            productId: Number.parseInt(params.productId),
            limit: 1000,
          }),
      }),
    ]);
  },
  component: ProductComponent,
  pendingComponent: LoadingIndicator,
});
