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
  useOrganizationsServiceDeleteOrganizationById,
  useOrganizationsServiceGetOrganizationByIdKey,
  useProductsServiceGetOrganizationProductsKey,
} from '@/api/queries';
import {
  ApiError,
  OrganizationsService,
  ProductsService,
} from '@/api/requests';
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

const OrganizationComponent = () => {
  const params = Route.useParams();
  const navigate = useNavigate();
  const { toast } = useToast();

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
        queryKey: [useProductsServiceGetOrganizationProductsKey, params.orgId],
        queryFn: async () =>
          await ProductsService.getOrganizationProducts({
            organizationId: Number.parseInt(params.orgId),
            limit: 1000,
          }),
      },
    ],
  });

  const { mutateAsync: deleteOrganization } =
    useOrganizationsServiceDeleteOrganizationById({
      onSuccess() {
        toast({
          title: 'Delete Organization',
          description: 'Organization deleted successfully.',
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
                    <AlertDialogTitle>Delete organization</AlertDialogTitle>
                  </div>
                </AlertDialogHeader>
                <AlertDialogDescription>
                  Are you sure you want to delete this organization:{' '}
                  <span className='font-bold'>{organization.name}</span>?
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
          <CardDescription>
            <div className='flex flex-col'>
              <div>{organization.description}</div>
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
            </div>
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className='flex flex-row items-center justify-between pb-1.5 pr-0'>
                  Products
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {products?.data.map((product) => {
                return (
                  <TableRow key={product.id}>
                    <TableCell>
                      <div>
                        <Link
                          to={`/organizations/$orgId/products/$productId`}
                          params={{
                            orgId: organization.id.toString(),
                            productId: product.id.toString(),
                          }}
                          className='font-semibold text-blue-400 hover:underline'
                        >
                          {product.name}
                        </Link>
                      </div>
                      <div className='hidden text-sm text-muted-foreground md:inline'>
                        {product.description}
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

export const Route = createFileRoute('/_layout/organizations/$orgId/')({
  loader: async ({ context, params }) => {
    await Promise.allSettled([
      context.queryClient.ensureQueryData({
        queryKey: [useOrganizationsServiceGetOrganizationByIdKey, params.orgId],
        queryFn: () =>
          OrganizationsService.getOrganizationById({
            organizationId: Number.parseInt(params.orgId),
          }),
      }),
      context.queryClient.ensureQueryData({
        queryKey: [useProductsServiceGetOrganizationProductsKey, params.orgId],
        queryFn: () =>
          ProductsService.getOrganizationProducts({
            organizationId: Number.parseInt(params.orgId),
            limit: 1000,
          }),
      }),
    ]);
  },
  component: OrganizationComponent,
});
