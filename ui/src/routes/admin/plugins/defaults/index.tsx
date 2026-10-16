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

import type {
  DefaultPluginsCategory,
  PreconfiguredPluginDescriptor,
} from '@/api';
import {
  getAvailableGlobalDefaultPluginsOptions,
  getGlobalDefaultPluginsOptions,
  getGlobalDefaultPluginsQueryKey,
  resetGlobalDefaultPluginsMutation,
  setGlobalDefaultPluginsMutation,
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

const GlobalDefaultPluginsComponent = () => {
  const [defaultsQuery, availablePluginsQuery] = useQueries({
    queries: [
      {
        ...getGlobalDefaultPluginsOptions(),
        staleTime: routePrefetchStaleTime,
      },
      {
        ...getAvailableGlobalDefaultPluginsOptions(),
        staleTime: routePrefetchStaleTime,
      },
    ],
  });

  const onSuccess = (category: DefaultPluginsCategory, message: string) => {
    toast.info('Default plugins saved', {
      description: `${message} for "${getDefaultPluginsCategoryTitle(category)}".`,
    });
    return queryClient.invalidateQueries({
      queryKey: getGlobalDefaultPluginsQueryKey(),
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
    ...setGlobalDefaultPluginsMutation(),
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
    ...resetGlobalDefaultPluginsMutation(),
    onSuccess: (_, { path }) =>
      onSuccess(
        path.category as DefaultPluginsCategory,
        'Reset the default plugins to the built-in defaults'
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

  const availablePlugins: PreconfiguredPluginDescriptor[] =
    availablePluginsQuery.data.map((plugin) => ({
      id: plugin.id,
      type: plugin.type,
      displayName: plugin.displayName,
      summary: plugin.summary,
      description: plugin.description,
      options: [],
    }));

  return (
    <div className='flex flex-col gap-4'>
      <Card>
        <CardHeader>
          <CardTitle>Default Plugins</CardTitle>
          <CardDescription>
            Configure the plugins that are used by default if a run does not
            specify the plugins to use. Organizations can override these
            defaults. If no defaults are configured for a category, the built-in
            defaults are used.
          </CardDescription>
        </CardHeader>
      </Card>
      <DefaultPluginsEditor
        level='GLOBAL'
        defaults={defaultsQuery.data}
        availablePlugins={availablePlugins}
        editable
        pendingCategory={pendingCategory as DefaultPluginsCategory | undefined}
        onSave={(category, pluginIds) =>
          setDefaults({ path: { category }, body: pluginIds })
        }
        onReset={(category) => resetDefaults({ path: { category } })}
      />
    </div>
  );
};

export const Route = createFileRoute('/admin/plugins/defaults/')({
  loader: async ({ context: { queryClient } }) => {
    await Promise.all([
      queryClient.prefetchQuery({
        ...getGlobalDefaultPluginsOptions(),
        staleTime: routePrefetchStaleTime,
      }),
      queryClient.prefetchQuery({
        ...getAvailableGlobalDefaultPluginsOptions(),
        staleTime: routePrefetchStaleTime,
      }),
    ]);
  },
  component: GlobalDefaultPluginsComponent,
  pendingComponent: LoadingIndicator,
});
