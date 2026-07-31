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

import { useQuery } from '@tanstack/react-query';
import { createFileRoute, Link } from '@tanstack/react-router';
import { Boxes, Bug, Files, Scale, ShieldQuestion } from 'lucide-react';
import type { ReactNode } from 'react';
import z from 'zod';

import { getProductOptions } from '@/api/@tanstack/react-query.gen';
import { ProductFavoriteButton } from '@/components/favorite-button';
import { LoadingIndicator } from '@/components/loading-indicator';
import { QueryBoundary, QueryErrorFallback } from '@/components/query-boundary';
import { StatisticsCard } from '@/components/statistics-card';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { toastError } from '@/lib/toast';
import {
  filterByNameSearchParameterSchema,
  paginationSearchParameterSchema,
} from '@/schemas';
import { ProductIssuesStatisticsCard } from './-components/product-issues-statistics-card';
import { ProductPackagesStatisticsCard } from './-components/product-packages-statistics-card';
import { ProductRepositoriesStatisticsCard } from './-components/product-repositories-statistics-card';
import { ProductRepositoryTable } from './-components/product-repository-table';
import { ProductViolationsStatisticsCard } from './-components/product-violations-statistics-card';
import { ProductVulnerabilitiesStatisticsCard } from './-components/product-vulnerabilities-statistics-card';

type StatisticsFallbackProps = {
  value: ReactNode;
};

const ProductRepositoriesStatisticsFallback = ({
  value,
}: StatisticsFallbackProps) => (
  <Card className='col-span-2'>
    <CardHeader>
      <CardTitle>
        <div className='flex items-center justify-between'>
          <span className='text-sm font-semibold'>Repositories</span>
          <Files className='h-4 w-4' />
        </div>
      </CardTitle>
    </CardHeader>
    <CardContent className='text-sm'>
      <div className='flex min-h-8 items-center'>{value}</div>
    </CardContent>
  </Card>
);

const ProductStatisticsFallback = ({ value }: StatisticsFallbackProps) => (
  <div className='grid grid-cols-4 gap-2'>
    <StatisticsCard
      title='Vulnerabilities'
      icon={() => <ShieldQuestion className='h-4 w-4 text-orange-500' />}
      value={value}
      className='hover:bg-muted/50 h-full'
    />
    <StatisticsCard
      title='Issues'
      icon={() => <Bug className='h-4 w-4 text-orange-500' />}
      value={value}
      className='hover:bg-muted/50 h-full'
    />
    <StatisticsCard
      title='Rule Violations'
      icon={() => <Scale className='h-4 w-4 text-orange-500' />}
      value={value}
      className='hover:bg-muted/50 h-full'
    />
    <StatisticsCard
      title='Packages'
      icon={() => <Boxes className='h-4 w-4 text-orange-500' />}
      value={value}
      className='hover:bg-muted/50 h-full'
    />
  </div>
);

const ProductComponent = () => {
  const params = Route.useParams();

  const {
    data: product,
    error: prodError,
    isPending: prodIsPending,
    isError: prodIsError,
  } = useQuery({
    ...getProductOptions({
      path: { productId: Number.parseInt(params.productId) },
    }),
  });

  if (prodIsPending) {
    return <LoadingIndicator />;
  }

  if (prodIsError) {
    toastError('Unable to load data', prodError);
    return;
  }

  return (
    <div className='flex flex-col gap-2'>
      <div className='grid grid-cols-4 gap-2'>
        <Card className='col-span-2'>
          <CardHeader>
            <div>
              <div className='flex items-center gap-1.5'>
                <CardTitle>{product.name}</CardTitle>
                <ProductFavoriteButton
                  organizationId={params.orgId}
                  product={product}
                  size='xs'
                  variant='ghost'
                  className='size-6 p-0'
                />
              </div>
              <CardDescription>{product.description}</CardDescription>
            </div>
          </CardHeader>
        </Card>
        <QueryBoundary
          fallback={
            <ProductRepositoriesStatisticsFallback
              value={<Skeleton className='h-8 w-12' />}
            />
          }
          errorFallback={(props) => (
            <ProductRepositoriesStatisticsFallback
              value={<QueryErrorFallback {...props} />}
            />
          )}
          resetKey={`${product.id}-repositories`}
        >
          <ProductRepositoriesStatisticsCard
            className='col-span-2'
            orgId={params.orgId}
            productId={product.id.toString()}
          />
        </QueryBoundary>
      </div>
      <QueryBoundary
        fallback={
          <ProductStatisticsFallback
            value={<Skeleton className='h-8 w-12' />}
          />
        }
        errorFallback={(props) => (
          <ProductStatisticsFallback
            value={<QueryErrorFallback {...props} />}
          />
        )}
        resetKey={`${product.id}-statistics`}
      >
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
                { id: 'repositoriesCount', desc: true },
              ],
            }}
          >
            <ProductVulnerabilitiesStatisticsCard
              productId={product.id}
              className='hover:bg-muted/50 h-full'
            />
          </Link>
          <ProductIssuesStatisticsCard productId={product.id} />
          <ProductViolationsStatisticsCard productId={product.id} />
          <ProductPackagesStatisticsCard productId={product.id} />
        </div>
      </QueryBoundary>
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
  validateSearch: z.object({
    ...paginationSearchParameterSchema.shape,
    ...filterByNameSearchParameterSchema.shape,
  }),
  component: ProductComponent,
  pendingComponent: LoadingIndicator,
});
