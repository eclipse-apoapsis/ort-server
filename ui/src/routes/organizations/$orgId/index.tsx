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

import { createFileRoute } from '@tanstack/react-router';
import { Boxes, Bug, Scale, ShieldQuestion } from 'lucide-react';
import { Suspense } from 'react';

import { useOrganizationsServiceGetApiV1OrganizationsByOrganizationId } from '@/api/queries';
import { prefetchUseOrganizationsServiceGetApiV1OrganizationsByOrganizationId } from '@/api/queries/prefetch';
import { LoadingIndicator } from '@/components/loading-indicator';
import { StatisticsCard } from '@/components/statistics-card';
import { ToastError } from '@/components/toast-error';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { toast } from '@/lib/toast';
import { paginationSearchParameterSchema } from '@/schemas';
import { OrganizationIssuesStatisticsCard } from './-components/organization-issues-statistics-card';
import { OrganizationPackagesStatisticsCard } from './-components/organization-packages-statistics-card';
import { OrganizationProductTable } from './-components/organization-product-table';
import { OrganizationProductsStatisticsCard } from './-components/organization-products-statistics-card';
import { OrganizationViolationsStatisticsCard } from './-components/organization-violations-statistics-card';
import { OrganizationVulnerabilitiesStatisticsCard } from './-components/organization-vulnerabilities-statistics-card';

const OrganizationComponent = () => {
  const params = Route.useParams();

  const {
    data: organization,
    error: orgError,
    isPending: orgIsPending,
    isError: orgIsError,
  } = useOrganizationsServiceGetApiV1OrganizationsByOrganizationId({
    organizationId: Number.parseInt(params.orgId),
  });

  if (orgIsPending) {
    return <LoadingIndicator />;
  }

  if (orgIsError) {
    toast.error('Unable to load data', {
      description: <ToastError error={orgError} />,
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
            <CardTitle>{organization.name}</CardTitle>
            <CardDescription>{organization.description}</CardDescription>
          </CardHeader>
        </Card>
        <OrganizationProductsStatisticsCard
          className='col-span-2'
          orgId={params.orgId}
        />
      </div>
      <div className='grid grid-cols-4 gap-2'>
        <Suspense
          fallback={
            <StatisticsCard
              title='Vulnerabilities'
              icon={() => (
                <ShieldQuestion className='h-4 w-4 text-orange-500' />
              )}
              value={<LoadingIndicator />}
              className='hover:bg-muted/50 h-full'
            />
          }
        >
          <OrganizationVulnerabilitiesStatisticsCard
            organizationId={organization.id}
          />
        </Suspense>

        <Suspense
          fallback={
            <StatisticsCard
              title='Issues'
              icon={() => <Bug className='h-4 w-4 text-orange-500' />}
              value={<LoadingIndicator />}
              className='hover:bg-muted/50 h-full'
            />
          }
        >
          <OrganizationIssuesStatisticsCard organizationId={organization.id} />
        </Suspense>
        <Suspense
          fallback={
            <StatisticsCard
              title='Rule Violations'
              icon={() => <Scale className='h-4 w-4 text-orange-500' />}
              value={<LoadingIndicator />}
              className='hover:bg-muted/50 h-full'
            />
          }
        >
          <OrganizationViolationsStatisticsCard
            organizationId={organization.id}
          />
        </Suspense>
        <Suspense
          fallback={
            <StatisticsCard
              title='Packages'
              icon={() => <Boxes className='h-4 w-4 text-orange-500' />}
              value={<LoadingIndicator />}
              className='hover:bg-muted/50 h-full'
            />
          }
        >
          <OrganizationPackagesStatisticsCard
            organizationId={organization.id}
          />
        </Suspense>
      </div>
      <Card>
        <CardContent className='my-4'>
          <OrganizationProductTable />
        </CardContent>
      </Card>
    </div>
  );
};

export const Route = createFileRoute('/organizations/$orgId/')({
  validateSearch: paginationSearchParameterSchema,
  loader: async ({ context, params }) => {
    await prefetchUseOrganizationsServiceGetApiV1OrganizationsByOrganizationId(
      context.queryClient,
      {
        organizationId: Number.parseInt(params.orgId),
      }
    );
  },
  component: OrganizationComponent,
  pendingComponent: LoadingIndicator,
});
