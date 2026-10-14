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

import { useSuspenseQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';

import {
  getOrganizationDefaultPluginsOptions,
  getPluginsForOrganizationOptions,
} from '@/api/@tanstack/react-query.gen';
import {
  resetOrganizationDefaultPlugins,
  setOrganizationDefaultPlugins,
} from '@/api/sdk.gen';
import { DefaultPluginsPage } from '@/components/default-plugins';
import { LoadingIndicator } from '@/components/loading-indicator';
import { routePrefetchStaleTime } from '@/lib/query-client';

const OrganizationDefaultPluginsComponent = () => {
  const params = Route.useParams();
  const organizationId = Number.parseInt(params.orgId);

  const { data: defaults } = useSuspenseQuery({
    ...getOrganizationDefaultPluginsOptions({ path: { organizationId } }),
    staleTime: routePrefetchStaleTime,
  });
  const { data: availablePlugins } = useSuspenseQuery({
    ...getPluginsForOrganizationOptions({ path: { organizationId } }),
    staleTime: routePrefetchStaleTime,
  });

  return (
    <DefaultPluginsPage
      level='ORGANIZATION'
      description='Configure the plugins that are used by default for the repositories of this organization if a run does not specify the plugins to use. If no defaults are configured for a category, the global defaults are used.'
      defaults={defaults}
      availablePlugins={availablePlugins}
      setDefaults={(category, pluginIds) =>
        setOrganizationDefaultPlugins({
          path: { organizationId, category },
          body: pluginIds,
          throwOnError: true,
        })
      }
      resetDefaults={(category) =>
        resetOrganizationDefaultPlugins({
          path: { organizationId, category },
          throwOnError: true,
        })
      }
    />
  );
};

export const Route = createFileRoute('/organizations/$orgId/default-plugins/')({
  loader: async ({ context: { queryClient }, params }) => {
    const organizationId = Number.parseInt(params.orgId);

    await Promise.all([
      queryClient.ensureQueryData({
        ...getOrganizationDefaultPluginsOptions({ path: { organizationId } }),
        staleTime: routePrefetchStaleTime,
      }),
      queryClient.ensureQueryData({
        ...getPluginsForOrganizationOptions({ path: { organizationId } }),
        staleTime: routePrefetchStaleTime,
      }),
    ]);
  },
  // Discard the local changes when switching to another organization.
  remountDeps: ({ params }) => params.orgId,
  component: OrganizationDefaultPluginsComponent,
  pendingComponent: LoadingIndicator,
});
