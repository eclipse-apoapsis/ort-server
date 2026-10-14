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

import {
  useMutation,
  type QueryKey,
  type UseQueryResult,
} from '@tanstack/react-query';

import type {
  DefaultPlugins,
  DefaultPluginsCategory,
  PreconfiguredPluginDescriptor,
} from '@/api';
import { LoadingIndicator } from '@/components/loading-indicator';
import {
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { queryClient } from '@/lib/query-client';
import { toast, toastError } from '@/lib/toast';
import { DefaultPluginsEditor } from './default-plugins-editor';
import {
  getDefaultPluginsCategoryTitle,
  type DefaultPluginsLevel,
} from './utils';

type DefaultPluginsPageProps = {
  /** The level on which the defaults are edited. */
  level: DefaultPluginsLevel;
  /** A description of the defaults shown at the top of the page. */
  description: string;
  /** The query for the current default plugins of all categories. */
  defaultsQuery: UseQueryResult<DefaultPlugins[]>;
  /** The key of the defaults query, which is invalidated after the defaults were changed. */
  defaultsQueryKey: QueryKey;
  /** The query for the plugins that can be selected as default plugins. */
  availablePluginsQuery: UseQueryResult<PreconfiguredPluginDescriptor[]>;
  setDefaults: (
    category: DefaultPluginsCategory,
    pluginIds: string[]
  ) => Promise<unknown>;
  resetDefaults: (category: DefaultPluginsCategory) => Promise<unknown>;
};

/** A page to edit the default plugins of all categories on one level. */
export const DefaultPluginsPage = ({
  level,
  description,
  defaultsQuery,
  defaultsQueryKey,
  availablePluginsQuery,
  setDefaults,
  resetDefaults,
}: DefaultPluginsPageProps) => {
  const onSuccess = (
    title: string,
    message: string,
    category: DefaultPluginsCategory
  ) => {
    toast.info(title, {
      description: `${message} for "${getDefaultPluginsCategoryTitle(category)}".`,
    });
    return queryClient.invalidateQueries({ queryKey: defaultsQueryKey });
  };

  const onError = (error: Error) => toastError(error.message, error);

  const save = useMutation({
    mutationFn: ({
      category,
      pluginIds,
    }: {
      category: DefaultPluginsCategory;
      pluginIds: string[];
    }) => setDefaults(category, pluginIds),
    onSuccess: (_, { category }) =>
      onSuccess('Default plugins saved', 'Saved the default plugins', category),
    onError,
  });

  const reset = useMutation({
    mutationFn: resetDefaults,
    onSuccess: (_, category) =>
      onSuccess(
        'Default plugins reset',
        `Reset the default plugins to the ${level === 'GLOBAL' ? 'built-in' : 'global'} defaults`,
        category
      ),
    onError,
  });

  const pendingCategory = save.isPending
    ? save.variables.category
    : reset.isPending
      ? reset.variables
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
          <CardDescription>{description}</CardDescription>
        </CardHeader>
      </Card>
      <DefaultPluginsEditor
        level={level}
        defaults={defaultsQuery.data}
        availablePlugins={availablePluginsQuery.data}
        pendingCategory={pendingCategory}
        onSave={(category, pluginIds) => save.mutate({ category, pluginIds })}
        onReset={(category) => reset.mutate(category)}
      />
    </div>
  );
};
