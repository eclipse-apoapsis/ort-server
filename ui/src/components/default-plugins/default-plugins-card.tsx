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

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Loader2 } from 'lucide-react';
import { useState } from 'react';

import type { DefaultPlugins, PreconfiguredPluginDescriptor } from '@/api';
import { PluginList } from '@/components/form/plugin-multi-select-field/plugin-list';
import { PluginListItem } from '@/components/form/plugin-multi-select-field/plugin-list-item';
import {
  getPluginsInDisplayOrder,
  moveItem,
} from '@/components/form/plugin-multi-select-field/utils';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { toast, toastError } from '@/lib/toast';
import {
  getDefaultPluginsOriginLabel,
  getUnavailablePlugins,
  invalidateDefaultPlugins,
  normalizePluginIds,
  pluginIdsDiffer,
  type DefaultPluginsCategoryInfo,
  type DefaultPluginsLevel,
} from './utils';

/** Unsaved changes of the default plugins. */
type Draft = {
  pluginIds: string[];
  /** The display order of all plugins, which is kept so that plugins do not move when selected. */
  pluginOrder: string[];
};

type DefaultPluginsCardProps = {
  category: DefaultPluginsCategoryInfo;
  /** The level on which the defaults are edited. */
  level: DefaultPluginsLevel;
  /** The current default plugins. */
  defaults: DefaultPlugins;
  /** The plugins that can be selected as default plugins. */
  availablePlugins: readonly PreconfiguredPluginDescriptor[];
  setDefaults: (pluginIds: string[]) => Promise<unknown>;
  resetDefaults: () => Promise<unknown>;
};

/** A card to edit the default plugins of one category. */
export const DefaultPluginsCard = ({
  category: { pluginType, title, description, isOrdered },
  level,
  defaults,
  availablePlugins,
  setDefaults,
  resetDefaults,
}: DefaultPluginsCardProps) => {
  const queryClient = useQueryClient();

  const defaultPluginIds = normalizePluginIds(
    defaults.pluginIds,
    availablePlugins
  );

  // Without local changes, the card always shows the current defaults.
  const [draft, setDraft] = useState<Draft>();
  const selectedPluginIds = draft?.pluginIds ?? defaultPluginIds;
  const selectedPluginIdSet = new Set(selectedPluginIds);

  // Unavailable plugins are shown if they are configured or still selected.
  const plugins = [
    ...availablePlugins,
    ...getUnavailablePlugins(
      pluginType,
      [...new Set([...defaultPluginIds, ...selectedPluginIds])],
      availablePlugins
    ),
  ];
  // Selected plugins are only shown first if their order matters.
  const pluginsInDisplayOrder = isOrdered
    ? getPluginsInDisplayOrder(plugins, selectedPluginIds, draft?.pluginOrder)
    : plugins;
  const pluginOrder = pluginsInDisplayOrder.map((plugin) => plugin.id);

  // Store the selection in display order. A selection equal to the defaults is no local change.
  const change = (nextPluginOrder: string[], selected: ReadonlySet<string>) => {
    const pluginIds = nextPluginOrder.filter((id) => selected.has(id));

    setDraft(
      pluginIdsDiffer(pluginIds, defaultPluginIds, isOrdered)
        ? { pluginIds, pluginOrder: nextPluginOrder }
        : undefined
    );
  };

  const setSelected = (pluginId: string, selected: boolean) => {
    const nextSelected = new Set(selectedPluginIdSet);

    if (selected) {
      nextSelected.add(pluginId);
    } else {
      nextSelected.delete(pluginId);
    }

    change(pluginOrder, nextSelected);
  };

  const reorder = (fromIndex: number, toIndex: number) =>
    change(moveItem(pluginOrder, fromIndex, toIndex), selectedPluginIdSet);

  // Saves the given plugin ids, or resets the defaults if they are null.
  const mutation = useMutation({
    mutationFn: (pluginIds: string[] | null) =>
      pluginIds ? setDefaults(pluginIds) : resetDefaults(),
    onSuccess: async (_, pluginIds) => {
      toast.info(
        pluginIds ? 'Default plugins saved' : 'Default plugins reset',
        {
          description: `${pluginIds ? 'Saved' : 'Reset'} the default plugins for "${title}".`,
        }
      );
      // The card stays pending until the new defaults are loaded, so that the local changes are
      // only discarded when the saved defaults can be shown.
      await invalidateDefaultPlugins(queryClient);
      setDraft(undefined);
    },
    onError: (error) => toastError(error.message, error),
  });
  const isPending = mutation.isPending;

  const progress = (label: string) => (
    <>
      <span className='sr-only'>{label}</span>
      <Loader2 size={16} className='mx-3 animate-spin' />
    </>
  );

  // Defaults that fall back to another category are not configured for this category.
  const isCustomized = defaults.source === level && !defaults.fallbackCategory;

  return (
    <Card>
      <CardHeader>
        <div className='flex items-center justify-between gap-2'>
          <CardTitle>{title}</CardTitle>
          <Badge variant={isCustomized ? 'default' : 'secondary'}>
            {isCustomized
              ? 'Customized'
              : getDefaultPluginsOriginLabel(defaults)}
          </Badge>
        </div>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent className='flex flex-col gap-2'>
        {plugins.length === 0 ? (
          <div className='text-muted-foreground text-sm'>
            No plugins are available.
          </div>
        ) : (
          <PluginList
            plugins={pluginsInDisplayOrder}
            enableReordering={isOrdered && !isPending}
            onReorder={reorder}
            renderItem={(plugin, dragHandle) => (
              <fieldset disabled={isPending}>
                <PluginListItem
                  plugin={plugin}
                  selected={selectedPluginIdSet.has(plugin.id)}
                  onSelectedChange={(selected) =>
                    setSelected(plugin.id, selected)
                  }
                  dragHandle={dragHandle}
                />
              </fieldset>
            )}
          />
        )}
      </CardContent>
      <CardFooter className='flex gap-2'>
        <Button
          type='button'
          variant='outline'
          disabled={!draft || isPending}
          onClick={() => setDraft(undefined)}
        >
          Discard Changes
        </Button>
        <Button
          type='button'
          variant='outline'
          disabled={!isCustomized || isPending}
          onClick={() => mutation.mutate(null)}
        >
          {isPending && mutation.variables === null
            ? progress('Resetting ...')
            : 'Reset to Inherited Defaults'}
        </Button>
        <Button
          type='button'
          disabled={!(draft || !isCustomized) || isPending}
          // Unavailable plugins are rejected by the backend, so they are removed when saving.
          onClick={() =>
            mutation.mutate(
              selectedPluginIds.filter((pluginId) =>
                availablePlugins.some((plugin) => plugin.id === pluginId)
              )
            )
          }
        >
          {isPending && mutation.variables !== null
            ? progress('Saving ...')
            : 'Save'}
        </Button>
      </CardFooter>
    </Card>
  );
};
