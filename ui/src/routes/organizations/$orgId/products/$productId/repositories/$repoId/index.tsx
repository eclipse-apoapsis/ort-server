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

import { createFileRoute, redirect } from '@tanstack/react-router';

import { UseRepositoriesServiceGetOrtRunsByRepositoryIdKeyFn } from '@/api/queries';
import { RepositoriesService } from '@/api/requests';
import { LoadingIndicator } from '@/components/loading-indicator';

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/repositories/$repoId/'
)({
  loader: async ({ params, context: { queryClient }, preload }) => {
    const queryKey = UseRepositoriesServiceGetOrtRunsByRepositoryIdKeyFn({
      repositoryId: Number.parseInt(params.repoId),
      limit: 1,
      sort: '-index',
    });

    const { data } = await queryClient.fetchQuery({
      queryKey,
      queryFn: () =>
        RepositoriesService.getOrtRunsByRepositoryId({
          repositoryId: Number.parseInt(params.repoId),
          limit: 1,
          sort: '-index',
        }),
      staleTime: 1000,
    });

    const firstRun = data[0];

    if (!preload) {
      if (firstRun) {
        throw redirect({
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex',
          replace: true,
          params: {
            orgId: params.orgId,
            productId: params.productId,
            repoId: params.repoId,
            runIndex: firstRun.index.toString(),
          },
        });
      } else {
        throw redirect({
          to: '/organizations/$orgId/products/$productId/repositories/$repoId/create-run',
          replace: true,
          params: {
            orgId: params.orgId,
            productId: params.productId,
            repoId: params.repoId,
          },
        });
      }
    }
  },
  pendingComponent: LoadingIndicator,
});
