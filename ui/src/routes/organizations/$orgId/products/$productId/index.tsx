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
import { Boxes, Bug, EditIcon, Scale, ShieldQuestion } from 'lucide-react';
import { Suspense } from 'react';

import {
  useProductsServiceDeleteProductById,
  useProductsServiceGetProductById,
} from '@/api/queries';
import { prefetchUseProductsServiceGetProductById } from '@/api/queries/prefetch';
import { ApiError } from '@/api/requests';
import { DeleteDialog } from '@/components/delete-dialog';
import { DeleteIconButton } from '@/components/delete-icon-button';
import { LoadingIndicator } from '@/components/loading-indicator';
import { StatisticsCard } from '@/components/statistics-card';
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
import {
  paginationSearchParameterSchema,
  sortingSearchParameterSchema,
} from '@/schemas';
import { ProductIssuesStatisticsCard } from './-components/product-issues-statistics-card';
import { ProductPackagesStatisticsCard } from './-components/product-packages-statistics-card';
import { ProductRepositoriesStatisticsCard } from './-components/product-repositories-statistics-card';
import { ProductRepositoryTable } from './-components/product-repository-table';
import { ProductViolationsStatisticsCard } from './-components/product-violations-statistics-card';
import { ProductVulnerabilitiesStatisticsCard } from './-components/product-vulnerabilities-statistics-card';

const ProductComponent = () => {
  const params = Route.useParams();
  const navigate = Route.useNavigate();

  const {
    data: product,
    error: prodError,
    isPending: prodIsPending,
    isError: prodIsError,
  } = useProductsServiceGetProductById({
    productId: Number.parseInt(params.productId),
  });

  const { mutateAsync: deleteProduct } = useProductsServiceDeleteProductById({
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

  if (prodIsPending) {
    return <LoadingIndicator />;
  }

  if (prodIsError) {
    toast.error('Unable to load data', {
      description: <ToastError error={prodError} />,
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
    return;
  }

  return (
    <div className='flex flex-col gap-2'>
      <div className='grid grid-cols-4 gap-2'>
        <Card className='col-span-2'>
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
                description={
                  <>
                    Are you sure you want to delete the product{' '}
                    <span className='font-bold'>{product.name}</span>?
                  </>
                }
                confirmationText={product.name}
                onDelete={handleDelete}
                trigger={<DeleteIconButton />}
              />
            </CardTitle>
            <CardDescription>{product.description}</CardDescription>
          </CardHeader>
        </Card>
        <ProductRepositoriesStatisticsCard
          className='col-span-2'
          orgId={params.orgId}
          productId={product.id.toString()}
        />
      </div>
      <div className='grid grid-cols-4 gap-2'>
        <Link
          to='/organizations/$orgId/products/$productId/vulnerabilities'
          params={{
            orgId: params.orgId,
            productId: params.productId,
          }}
          search={{
            sortBy: [
              { id: 'rating', desc: true },
              { id: 'count', desc: true },
            ],
          }}
        >
          <Suspense
            fallback={
              <StatisticsCard
                title='Vulnerabilities'
                icon={() => (
                  <ShieldQuestion className='h-4 w-4 text-orange-500' />
                )}
                value={<LoadingIndicator />}
                className='h-full hover:bg-muted/50'
              />
            }
          >
            <ProductVulnerabilitiesStatisticsCard productId={product.id} />
          </Suspense>
        </Link>
        <Suspense
          fallback={
            <StatisticsCard
              title='Issues'
              icon={() => <Bug className='h-4 w-4 text-orange-500' />}
              value={<LoadingIndicator />}
              className='h-full hover:bg-muted/50'
            />
          }
        >
          <ProductIssuesStatisticsCard productId={product.id} />
        </Suspense>
        <Suspense
          fallback={
            <StatisticsCard
              title='Rule Violations'
              icon={() => <Scale className='h-4 w-4 text-orange-500' />}
              value={<LoadingIndicator />}
              className='h-full hover:bg-muted/50'
            />
          }
        >
          <ProductViolationsStatisticsCard productId={product.id} />
        </Suspense>
        <Suspense
          fallback={
            <StatisticsCard
              title='Packages'
              icon={() => <Boxes className='h-4 w-4 text-orange-500' />}
              value={<LoadingIndicator />}
              className='h-full hover:bg-muted/50'
            />
          }
        >
          <ProductPackagesStatisticsCard productId={product.id} />
        </Suspense>
      </div>
      <Card>
        <CardContent className='my-4'>
          <ProductRepositoryTable />
        </CardContent>
      </Card>
    </div>
  );
};

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/'
)({
  validateSearch: paginationSearchParameterSchema.merge(
    sortingSearchParameterSchema
  ),
  loader: async ({ context, params }) => {
    await prefetchUseProductsServiceGetProductById(context.queryClient, {
      productId: Number.parseInt(params.productId),
    });
  },
  component: ProductComponent,
  pendingComponent: LoadingIndicator,
});
