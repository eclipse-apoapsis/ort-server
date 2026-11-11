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

import type { QueryClient } from '@tanstack/react-query';

import type { OrtRunStatus } from '@/api';
import {
  getGlobalDefaultPluginsOptions,
  getInstalledPluginsOptions,
  getOrganizationsOptions,
  getProductsOptions,
  getRepositoriesOptions,
  getRunsOptions,
  getUsersOptions,
} from '@/api/@tanstack/react-query.gen';
import { config } from '@/config';

// The queries of the admin overview, shared by the route loader, which starts them, and the
// components, which read them.
//
// Each query is built by a function rather than a constant: the generated options put the client's
// base URL into the query key, and a constant would be built when the module is imported, before
// the app has set the base URL of the deployment.

export const organizationsCountOptions = () =>
  getOrganizationsOptions({ query: { limit: 1 } });

export const productsCountOptions = () =>
  getProductsOptions({ query: { limit: 1 } });

export const repositoriesCountOptions = () =>
  getRepositoriesOptions({ query: { limit: 1 } });

export const usersCountOptions = () => getUsersOptions({ query: { limit: 1 } });

export const installedPluginsOptions = () => getInstalledPluginsOptions();

export const defaultPluginsOptions = () => getGlobalDefaultPluginsOptions();

export const runsCountOptions = () => ({
  ...getRunsOptions({ query: { limit: 1 } }),
  refetchInterval: config.pollInterval,
});

// The run statuses, the end states first, as they make up most runs.
export const runStatuses = [
  'FINISHED',
  'FINISHED_WITH_ISSUES',
  'FAILED',
  'ACTIVE',
  'CREATED',
] as const satisfies readonly OrtRunStatus[];

// The statuses a run ends in, which the status bar shows.
export const endStatuses = [
  'FINISHED',
  'FINISHED_WITH_ISSUES',
  'FAILED',
] as const satisfies readonly OrtRunStatus[];

export const runStatusCountOptions = (status: OrtRunStatus) => ({
  ...getRunsOptions({ query: { limit: 1, status: status.toLowerCase() } }),
  refetchInterval: config.pollInterval,
});

/**
 * Start all queries of the overview without waiting for them.
 */
export const prefetchOverview = (queryClient: QueryClient) => {
  void queryClient.prefetchQuery(organizationsCountOptions());
  void queryClient.prefetchQuery(productsCountOptions());
  void queryClient.prefetchQuery(repositoriesCountOptions());
  void queryClient.prefetchQuery(usersCountOptions());
  void queryClient.prefetchQuery(installedPluginsOptions());
  void queryClient.prefetchQuery(defaultPluginsOptions());
  void queryClient.prefetchQuery(runsCountOptions());
  runStatuses.forEach(
    (status) => void queryClient.prefetchQuery(runStatusCountOptions(status))
  );
};
