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

import { useQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';

import type { PluginDescriptor, PreconfiguredPluginDescriptor } from '@/api';
import {
  getAvailableGlobalDefaultPluginsOptions,
  getGlobalDefaultPluginsOptions,
  getGlobalDefaultPluginsQueryKey,
} from '@/api/@tanstack/react-query.gen';
import {
  resetGlobalDefaultPlugins,
  setGlobalDefaultPlugins,
} from '@/api/sdk.gen';
import { DefaultPluginsPage } from '@/components/default-plugins';
import { LoadingIndicator } from '@/components/loading-indicator';
import { routePrefetchStaleTime } from '@/lib/query-client';

/** Convert a plugin descriptor to the type that can be selected as default plugin. */
const toPreconfiguredPlugin = (
  plugin: PluginDescriptor
): PreconfiguredPluginDescriptor => ({
  id: plugin.id,
  type: plugin.type,
  displayName: plugin.displayName,
  summary: plugin.summary,
  description: plugin.description,
  options: [],
});

const GlobalDefaultPluginsComponent = () => {
  const defaultsQuery = useQuery({
    ...getGlobalDefaultPluginsOptions(),
    staleTime: routePrefetchStaleTime,
  });
  const availablePluginsQuery = useQuery({
    ...getAvailableGlobalDefaultPluginsOptions(),
    staleTime: routePrefetchStaleTime,
    select: (plugins) => plugins.map(toPreconfiguredPlugin),
  });

  return (
    <DefaultPluginsPage
      level='GLOBAL'
      description='Configure the plugins that are used by default if a run does not specify the plugins to use. Organizations can override these defaults. If no defaults are configured for a category, the built-in defaults are used.'
      defaultsQuery={defaultsQuery}
      defaultsQueryKey={getGlobalDefaultPluginsQueryKey()}
      availablePluginsQuery={availablePluginsQuery}
      setDefaults={(category, pluginIds) =>
        setGlobalDefaultPlugins({
          path: { category },
          body: pluginIds,
          throwOnError: true,
        })
      }
      resetDefaults={(category) =>
        resetGlobalDefaultPlugins({ path: { category }, throwOnError: true })
      }
    />
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
