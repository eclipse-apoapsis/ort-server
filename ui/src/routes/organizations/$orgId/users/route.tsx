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

import { createFileRoute, Outlet, redirect } from '@tanstack/react-router';

import { ensureUseDefaultServiceGetApiV1OrganizationsByOrganizationIdUsersData } from '@/api/queries/ensureQueryData.ts';
import { paginationSearchParameterSchema } from '@/schemas';

export const Route = createFileRoute('/organizations/$orgId/users')({
  component: () => <Outlet />,

  // Route’s query string parameters (centralized)
  validateSearch: paginationSearchParameterSchema,
  loaderDeps: ({ search: { page, pageSize } }) => ({
    page,
    pageSize,
  }),
  loader: async ({ context, deps, params }) => {
    const { queryClient } = context;
    const { page = 1, pageSize = 10 } = deps;
    const { orgId } = params;
    const pageIndex = page - 1;

    // Ensure the data is available in the query cache when the component is rendered.
    await ensureUseDefaultServiceGetApiV1OrganizationsByOrganizationIdUsersData(
      queryClient,
      {
        limit: pageSize,
        offset: pageIndex * pageSize,
        organizationId: Number.parseInt(orgId),
        sort: 'username',
      }
    );
  },
  beforeLoad: ({ context, params }) => {
    if (
      !context.auth.hasRole([
        'superuser',
        `role_organization_${params.orgId}_admin`,
      ])
    ) {
      throw redirect({
        to: '/403',
      });
    }
  },
});
