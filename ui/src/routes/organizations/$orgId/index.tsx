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

import { useOrganizationsServiceGetOrganizationById } from '@/api/queries';
import { prefetchUseOrganizationsServiceGetOrganizationById } from '@/api/queries/prefetch';
import { LoadingIndicator } from '@/components/loading-indicator';
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
import { OrganizationProductTable } from './-components/organization-product-table';
import { OrganizationProductsStatisticsCard } from './-components/organization-products-statistics-card';

const OrganizationComponent = () => {
  const params = Route.useParams();

  const {
    data: organization,
    error: orgError,
    isPending: orgIsPending,
    isError: orgIsError,
  } = useOrganizationsServiceGetOrganizationById({
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
      <div className='grid grid-cols-4 gap-2'></div>
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
    await prefetchUseOrganizationsServiceGetOrganizationById(
      context.queryClient,
      {
        organizationId: Number.parseInt(params.orgId),
      }
    );
  },
  component: OrganizationComponent,
  pendingComponent: LoadingIndicator,
});
