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

import { getOrganizationOptions } from '@/api/@tanstack/react-query.gen';
import { OrganizationFavoriteButton } from '@/components/favorite-button';
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
import { OrganizationIssuesStatisticsCard } from './-components/organization-issues-statistics-card';
import { OrganizationPackagesStatisticsCard } from './-components/organization-packages-statistics-card';
import { OrganizationProductTable } from './-components/organization-product-table';
import { OrganizationProductsStatisticsCard } from './-components/organization-products-statistics-card';
import { OrganizationViolationsStatisticsCard } from './-components/organization-violations-statistics-card';
import { OrganizationVulnerabilitiesStatisticsCard } from './-components/organization-vulnerabilities-statistics-card';

type StatisticsFallbackProps = {
  value: ReactNode;
};

const OrganizationProductsStatisticsFallback = ({
  value,
}: StatisticsFallbackProps) => (
  <Card className='col-span-2'>
    <CardHeader>
      <CardTitle>
        <div className='flex items-center justify-between'>
          <span className='text-sm font-semibold'>Products</span>
          <Files className='h-4 w-4' />
        </div>
      </CardTitle>
    </CardHeader>
    <CardContent className='text-sm'>
      <div className='flex min-h-8 items-center'>{value}</div>
    </CardContent>
  </Card>
);

const OrganizationStatisticsFallback = ({ value }: StatisticsFallbackProps) => (
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

const OrganizationComponent = () => {
  const params = Route.useParams();

  const {
    data: organization,
    error: orgError,
    isPending: orgIsPending,
    isError: orgIsError,
  } = useQuery({
    ...getOrganizationOptions({
      path: { organizationId: Number.parseInt(params.orgId) },
    }),
  });

  if (orgIsPending) {
    return <LoadingIndicator />;
  }

  if (orgIsError) {
    toastError('Unable to load data', orgError);
    return;
  }

  return (
    <div className='flex flex-col gap-2'>
      <div className='grid grid-cols-4 gap-2'>
        <Card className='col-span-2'>
          <CardHeader>
            <div>
              <div className='flex items-center gap-1.5'>
                <CardTitle>{organization.name}</CardTitle>
                <OrganizationFavoriteButton
                  organization={organization}
                  size='xs'
                  variant='ghost'
                  className='size-6 p-0'
                />
              </div>
              <CardDescription>{organization.description}</CardDescription>
            </div>
          </CardHeader>
        </Card>
        <QueryBoundary
          fallback={
            <OrganizationProductsStatisticsFallback
              value={<Skeleton className='h-8 w-12' />}
            />
          }
          errorFallback={(props) => (
            <OrganizationProductsStatisticsFallback
              value={<QueryErrorFallback {...props} />}
            />
          )}
          resetKey={`${organization.id}-products`}
        >
          <OrganizationProductsStatisticsCard
            className='col-span-2'
            orgId={params.orgId}
          />
        </QueryBoundary>
      </div>
      <QueryBoundary
        fallback={
          <OrganizationStatisticsFallback
            value={<Skeleton className='h-8 w-12' />}
          />
        }
        errorFallback={(props) => (
          <OrganizationStatisticsFallback
            value={<QueryErrorFallback {...props} />}
          />
        )}
        resetKey={`${organization.id}-statistics`}
      >
        <div className='grid grid-cols-4 gap-2'>
          <Link
            to='/organizations/$orgId/vulnerabilities'
            params={{
              orgId: params.orgId,
            }}
            search={{
              sortBy: [
                { id: 'rating', desc: true },
                { id: 'repositoriesCount', desc: true },
              ],
            }}
          >
            <OrganizationVulnerabilitiesStatisticsCard
              organizationId={organization.id}
              className='hover:bg-muted/50 h-full'
            />
          </Link>
          <OrganizationIssuesStatisticsCard organizationId={organization.id} />
          <OrganizationViolationsStatisticsCard
            organizationId={organization.id}
          />
          <OrganizationPackagesStatisticsCard
            organizationId={organization.id}
          />
        </div>
      </QueryBoundary>
      <Card>
        <CardContent className='my-4'>
          <OrganizationProductTable />
        </CardContent>
      </Card>
    </div>
  );
};

export const Route = createFileRoute('/organizations/$orgId/')({
  validateSearch: z.object({
    ...paginationSearchParameterSchema.shape,
    ...filterByNameSearchParameterSchema.shape,
  }),
  component: OrganizationComponent,
  pendingComponent: LoadingIndicator,
});
