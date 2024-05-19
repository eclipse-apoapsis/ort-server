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

import { useRepositoriesServiceGetRepositoryByIdKey } from '@/api/queries';
import { RepositoriesService } from '@/api/requests';
import { Outlet, createFileRoute } from '@tanstack/react-router';
import { Suspense } from 'react';

export const Route = createFileRoute(
  '/_layout/organizations/$orgId/products/$productId/repositories/$repoId'
)({
  loader: async ({ context, params }) => {
    const repo = await context.queryClient.ensureQueryData({
      queryKey: [useRepositoriesServiceGetRepositoryByIdKey, params.repoId],
      queryFn: () =>
        RepositoriesService.getRepositoryById({
          repositoryId: Number.parseInt(params.repoId),
        }),
    });
    context.breadcrumbs.repo = repo.url;
  },
  component: () => (
    <Suspense fallback={<div>Loading...</div>}>
      <Outlet />
    </Suspense>
  ),
});
