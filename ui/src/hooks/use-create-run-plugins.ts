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

import { keepPreviousData, useQuery } from '@tanstack/react-query';

import type { PreconfiguredPluginDescriptor } from '@/api';
import { getPluginsForRepositoryOptions } from '@/api/@tanstack/react-query.gen';

const NO_PLUGINS: PreconfiguredPluginDescriptor[] = [];

/**
 * The query options for the plugins available for creating a run in the given repository with the
 * given config context. The route loader and the page share them to use the same query.
 */
export const createRunPluginsOptions = (
  repositoryId: number,
  configContext?: string
) =>
  getPluginsForRepositoryOptions({
    path: { repositoryId },
    query: configContext ? { configContext } : undefined,
  });

/**
 * Load the plugins available for creating a run. The plugins are reported as loading until those
 * for the given config context are available, also while reloading them after a config context
 * change. Meanwhile, the previous plugins are kept so that the form does not lose its values.
 */
export const useCreateRunPlugins = (
  repositoryId: number,
  configContext: string
) => {
  const { data, error, isPending, isPlaceholderData } = useQuery({
    ...createRunPluginsOptions(repositoryId, configContext),
    placeholderData: keepPreviousData,
  });

  return {
    plugins: data ?? NO_PLUGINS,
    pluginsLoading: isPending || isPlaceholderData,
    pluginsError: error,
  };
};
