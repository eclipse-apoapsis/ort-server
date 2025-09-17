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
import { createFileRoute, useNavigate } from '@tanstack/react-router';

import { getOrtRunsByRepositoryIdOptions } from '@/api/@tanstack/react-query.gen';
import { LoadingIndicator } from '@/components/loading-indicator';
import { ToastError } from '@/components/toast-error';
import { toast } from '@/lib/toast';

const RunRedirectComponent = () => {
  const params = Route.useParams();
  const navigate = useNavigate();

  const {
    data: runs,
    isPending,
    isError,
    error,
  } = useQuery({
    ...getOrtRunsByRepositoryIdOptions({
      path: {
        repositoryId: Number.parseInt(params.repoId),
      },
      query: {
        limit: 1,
        sort: '-index',
      },
    }),
    staleTime: 1000,
  });

  if (isPending) {
    return <LoadingIndicator />;
  }

  if (isError) {
    toast.error('Unable to load data', {
      description: <ToastError error={error} />,
      duration: Infinity,
      cancel: {
        label: 'Dismiss',
        onClick: () => {},
      },
    });
    return;
  }

  const firstRun = runs.data[0];

  if (firstRun) {
    navigate({
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
    navigate({
      to: '/organizations/$orgId/products/$productId/repositories/$repoId/create-run',
      replace: true,
      params: {
        orgId: params.orgId,
        productId: params.productId,
        repoId: params.repoId,
      },
    });
  }
};

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/repositories/$repoId/'
)({
  component: RunRedirectComponent,
});
