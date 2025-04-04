/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import { prefetchUseOrganizationsServiceGetApiV1OrganizationsByOrganizationId } from '@/api/queries/prefetch';
import { useOrganizationsServiceGetApiV1OrganizationsByOrganizationIdVulnerabilitiesSuspense } from '@/api/queries/suspense';
import { LoadingIndicator } from '@/components/loading-indicator';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {
  paginationSearchParameterSchema,
  sortingSearchParameterSchema,
} from '@/schemas';
import { OrganizationVulnerabilityTable } from './-components/organization-vulnerability-table';

const OrganizationVulnerabilitiesComponent = () => {
  const params = Route.useParams();

  const { data: vulnerabilities } =
    useOrganizationsServiceGetApiV1OrganizationsByOrganizationIdVulnerabilitiesSuspense(
      {
        organizationId: Number.parseInt(params.orgId),
        limit: 1,
      }
    );
  const totalVulnerabilities = vulnerabilities.pagination.totalCount;

  return (
    <Card className='h-fit'>
      <CardHeader>
        <CardTitle>Vulnerabilities ({totalVulnerabilities} in total)</CardTitle>
        <CardDescription>
          These are the vulnerabilities found currently from this organization.
          Please note that the vulnerability status may change over time, as
          your dependencies change. Therefore, your organization's repositories
          should be scanned for vulnerabilities regularly.
        </CardDescription>
        <CardDescription>
          By clicking on "References" you can see more information about the
          vulnerability. The overall severity rating is calculated based on the
          highest severity rating found in the references.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <OrganizationVulnerabilityTable />
      </CardContent>
    </Card>
  );
};

export const Route = createFileRoute('/organizations/$orgId/vulnerabilities/')({
  validateSearch: paginationSearchParameterSchema.merge(
    sortingSearchParameterSchema
  ),
  loader: async ({ context, params }) => {
    await prefetchUseOrganizationsServiceGetApiV1OrganizationsByOrganizationId(
      context.queryClient,
      {
        organizationId: Number.parseInt(params.orgId),
      }
    );
  },
  component: OrganizationVulnerabilitiesComponent,
  pendingComponent: LoadingIndicator,
});
