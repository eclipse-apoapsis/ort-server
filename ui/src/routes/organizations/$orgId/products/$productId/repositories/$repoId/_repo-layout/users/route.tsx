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

import { createFileRoute, Outlet } from '@tanstack/react-router';

import { getRepositoryUsersOptions } from '@/api/@tanstack/react-query.gen';
import { RequireRepositoryPermission } from '@/components/authorization';
import { paginationSearchParameterSchema } from '@/schemas';

function RepositoryUsersGuard() {
  const { repoId } = Route.useParams();

  return (
    <RequireRepositoryPermission
      repositoryId={Number.parseInt(repoId)}
      permission='MANAGE_GROUPS'
    >
      <Outlet />
    </RequireRepositoryPermission>
  );
}

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/users'
)({
  component: RepositoryUsersGuard,

  // Route’s query string parameters (centralized)
  validateSearch: paginationSearchParameterSchema,
  loaderDeps: ({ search: { page, pageSize } }) => ({
    page,
    pageSize,
  }),
  loader: async ({ context: { queryClient }, deps, params }) => {
    const { page = 1, pageSize = 10 } = deps;
    const { repoId } = params;
    const pageIndex = page - 1;

    // Ensure the data is available in the query cache when the component is rendered.
    await queryClient.ensureQueryData({
      ...getRepositoryUsersOptions({
        path: {
          repositoryId: Number.parseInt(repoId),
        },
        query: {
          limit: pageSize,
          offset: pageIndex * pageSize,
          sort: 'username',
        },
      }),
    });
  },
});
