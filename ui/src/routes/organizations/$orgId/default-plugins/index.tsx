/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import { useMutation, useQueries } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';

import type { DefaultPluginsCategory } from '@/api';
import {
  getOrganizationDefaultPluginsOptions,
  getOrganizationDefaultPluginsQueryKey,
  getPluginsForOrganizationOptions,
  resetOrganizationDefaultPluginsMutation,
  setOrganizationDefaultPluginsMutation,
} from '@/api/@tanstack/react-query.gen';
import {
  DefaultPluginsEditor,
  getDefaultPluginsCategoryTitle,
} from '@/components/default-plugins';
import { LoadingIndicator } from '@/components/loading-indicator';
import {
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { ApiError } from '@/lib/api-error';
import { queryClient, routePrefetchStaleTime } from '@/lib/query-client';
import { toast, toastError } from '@/lib/toast';

const OrganizationDefaultPluginsComponent = () => {
  const params = Route.useParams();
  const organizationId = Number.parseInt(params.orgId);

  const [defaultsQuery, availablePluginsQuery] = useQueries({
    queries: [
      {
        ...getOrganizationDefaultPluginsOptions({ path: { organizationId } }),
        staleTime: routePrefetchStaleTime,
      },
      {
        ...getPluginsForOrganizationOptions({ path: { organizationId } }),
        staleTime: routePrefetchStaleTime,
      },
    ],
  });

  const onSuccess = (category: DefaultPluginsCategory, message: string) => {
    toast.info('Default plugins saved', {
      description: `${message} for "${getDefaultPluginsCategoryTitle(category)}".`,
    });
    return queryClient.invalidateQueries({
      queryKey: getOrganizationDefaultPluginsQueryKey({
        path: { organizationId },
      }),
    });
  };

  const onError = (error: ApiError) => {
    toastError(error.message, error);
  };

  const {
    mutate: setDefaults,
    isPending: isSetting,
    variables: setVariables,
  } = useMutation({
    ...setOrganizationDefaultPluginsMutation(),
    onSuccess: (_, { path }) =>
      onSuccess(
        path.category as DefaultPluginsCategory,
        'Saved the default plugins'
      ),
    onError,
  });

  const {
    mutate: resetDefaults,
    isPending: isResetting,
    variables: resetVariables,
  } = useMutation({
    ...resetOrganizationDefaultPluginsMutation(),
    onSuccess: (_, { path }) =>
      onSuccess(
        path.category as DefaultPluginsCategory,
        'Reset the default plugins to the global defaults'
      ),
    onError,
  });

  const pendingCategory = isSetting
    ? setVariables.path.category
    : isResetting
      ? resetVariables.path.category
      : undefined;

  if (defaultsQuery.isPending || availablePluginsQuery.isPending) {
    return <LoadingIndicator />;
  }

  if (defaultsQuery.isError) {
    toastError('Failed to load the default plugins', defaultsQuery.error);
    return;
  }

  if (availablePluginsQuery.isError) {
    toastError(
      'Failed to load the available plugins',
      availablePluginsQuery.error
    );
    return;
  }

  return (
    <div className='flex flex-col gap-4'>
      <Card>
        <CardHeader>
          <CardTitle>Default Plugins</CardTitle>
          <CardDescription>
            Configure the plugins that are used by default for the repositories
            of this organization if a run does not specify the plugins to use.
            If no defaults are configured for a category, the global defaults
            are used.
          </CardDescription>
        </CardHeader>
      </Card>
      <DefaultPluginsEditor
        level='ORGANIZATION'
        defaults={defaultsQuery.data}
        availablePlugins={availablePluginsQuery.data}
        editable
        pendingCategory={pendingCategory as DefaultPluginsCategory | undefined}
        onSave={(category, pluginIds) =>
          setDefaults({
            path: { organizationId, category },
            body: pluginIds,
          })
        }
        onReset={(category) =>
          resetDefaults({ path: { organizationId, category } })
        }
      />
    </div>
  );
};

export const Route = createFileRoute('/organizations/$orgId/default-plugins/')({
  loader: async ({ context: { queryClient }, params }) => {
    const organizationId = Number.parseInt(params.orgId);

    await Promise.all([
      queryClient.prefetchQuery({
        ...getOrganizationDefaultPluginsOptions({ path: { organizationId } }),
        staleTime: routePrefetchStaleTime,
      }),
      queryClient.prefetchQuery({
        ...getPluginsForOrganizationOptions({ path: { organizationId } }),
        staleTime: routePrefetchStaleTime,
      }),
    ]);
  },
  component: OrganizationDefaultPluginsComponent,
  pendingComponent: LoadingIndicator,
});
