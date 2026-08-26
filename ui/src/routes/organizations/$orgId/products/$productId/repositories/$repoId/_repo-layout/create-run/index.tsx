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

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { z } from 'zod';

import type { PostRepositoryRun } from '@/api';
import {
  getOrganizationOptions,
  getProductOptions,
  getRepositoryOptions,
  postRepositoryRunMutation,
} from '@/api/@tanstack/react-query.gen';
import {
  getAvailableRepositorySecrets,
  getPluginsForRepository,
  getRepositoryRun,
} from '@/api/sdk.gen';
import { LoadingIndicator } from '@/components/loading-indicator';
import { useUser } from '@/hooks/use-user.ts';
import { ApiError } from '@/lib/api-error';
import { runCreated } from '@/lib/entity-cache';
import { toast, toastError } from '@/lib/toast';
import { buildRecentRun, useHomeRecentRunActions } from '@/providers/home-data';
import { CreateRunForm } from './-components/create-run-form';

const CreateRunPage = () => {
  const navigate = useNavigate();
  const params = Route.useParams();
  const queryClient = useQueryClient();
  const { ortRun, plugins, secrets } = Route.useLoaderData();
  const { recordRecentRun } = useHomeRecentRunActions();
  const isSuperuser = useUser().isSuperuser || false;
  const permissions = Route.useRouteContext().permissions;

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

  const {
    data: product,
    error: productError,
    isPending: productIsPending,
    isError: productIsError,
  } = useQuery({
    ...getProductOptions({
      path: { productId: Number.parseInt(params.productId) },
    }),
  });

  const {
    data: repository,
    error: repositoryError,
    isPending: repositoryIsPending,
    isError: repositoryIsError,
  } = useQuery({
    ...getRepositoryOptions({
      path: { repositoryId: Number.parseInt(params.repoId) },
    }),
  });

  const { mutateAsync, isPending } = useMutation({
    ...postRepositoryRunMutation(),
    onSuccess(response) {
      if (organization && product && repository) {
        recordRecentRun(
          buildRecentRun(organization, product, repository, response)
        );
      }

      toast.info('Create Run', {
        description: 'New run created successfully for this repository.',
      });
      runCreated(queryClient, response.repositoryId);
      navigate({
        to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex',
        params: {
          orgId: params.orgId,
          productId: params.productId,
          repoId: params.repoId,
          runIndex: response.index.toString(),
        },
      });
    },
    onError(error: ApiError) {
      toastError(error.message, error);
    },
  });

  const submitRun = async (payload: PostRepositoryRun) => {
    await mutateAsync({
      path: {
        repositoryId: Number.parseInt(params.repoId),
      },
      body: payload,
    });
  };

  if (orgIsPending || productIsPending || repositoryIsPending) {
    return <LoadingIndicator />;
  }

  if (orgIsError || productIsError || repositoryIsError) {
    toastError(
      'Unable to load data',
      orgError || productError || repositoryError
    );
    return;
  }

  return (
    <CreateRunForm
      isSubmitting={isPending}
      isSuperuser={isSuperuser}
      onSubmit={submitRun}
      permissions={permissions}
      plugins={plugins.data ?? []}
      rerun={ortRun?.data ?? null}
      secrets={secrets.data ?? []}
    />
  );
};

const rerunIndexSchema = z.object({
  rerunIndex: z.number().optional(),
});

export const Route = createFileRoute(
  '/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/'
)({
  // This is used to access the search params in the loader.
  // As search params, we use the index of the ORT run on which this run will be based.
  loaderDeps: ({ search: { rerunIndex } }) => ({ rerunIndex }),
  // The loader fetches the ORT Run that is being rerun.
  // It is important to notice that if no rerunIndex is provided to this route,
  // the query will not be run. This corresponds to the "New run" case, where a new
  // ORT Run is created from scratch, using all defaults.
  loader: async ({ params, deps: { rerunIndex } }) => {
    const [ortRun, plugins, secrets] = await Promise.all([
      rerunIndex !== undefined
        ? getRepositoryRun({
            path: {
              repositoryId: Number.parseInt(params.repoId),
              ortRunIndex: rerunIndex,
            },
          })
        : Promise.resolve(null as null),
      getPluginsForRepository({
        path: {
          repositoryId: Number.parseInt(params.repoId),
        },
      }),
      getAvailableRepositorySecrets({
        path: {
          repositoryId: Number.parseInt(params.repoId),
        },
      }),
    ]);

    return {
      ortRun,
      plugins,
      secrets,
    };
  },
  component: CreateRunPage,
  validateSearch: rerunIndexSchema,
});
