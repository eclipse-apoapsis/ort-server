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

import { Loader2 } from 'lucide-react';
import { useState } from 'react';

import type {
  DefaultPlugins,
  PluginType,
  PreconfiguredPluginDescriptor,
} from '@/api';
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
import {
  getDefaultPluginsOriginLabel,
  getUnavailablePlugins,
  normalizePluginIds,
  pluginIdsDiffer,
  type DefaultPluginsLevel,
} from './utils';

type DefaultPluginsCardProps = {
  title: string;
  description: string;
  /** The type of the plugins that can be selected. */
  pluginType: PluginType;
  /** Whether the order of the default plugins matters, so that the user can reorder them. */
  isOrdered: boolean;
  /** The level on which the defaults are edited. */
  level: DefaultPluginsLevel;
  /** The current default plugins. The card must get a new `key` when they change. */
  defaults: DefaultPlugins;
  /** The plugins that can be selected as default plugins. */
  availablePlugins: readonly PreconfiguredPluginDescriptor[];
  /** Whether a request to change the defaults is in progress. */
  isPending: boolean;
  onSave: (pluginIds: string[]) => void;
  onReset: () => void;
};

/** A card to edit the default plugins of one category. */
export const DefaultPluginsCard = ({
  title,
  description,
  pluginType,
  isOrdered,
  level,
  defaults,
  availablePlugins,
  isPending,
  onSave,
  onReset,
}: DefaultPluginsCardProps) => {
  const defaultPluginIds = normalizePluginIds(
    defaults.pluginIds,
    availablePlugins
  );
  const plugins = [
    ...availablePlugins,
    ...getUnavailablePlugins(pluginType, defaultPluginIds, availablePlugins),
  ];

  const [selectedPluginIds, setSelectedPluginIds] =
    useState<string[]>(defaultPluginIds);
  // The order is fixed when the card is created, so that plugins do not move when they are
  // selected. Selected plugins are only shown first if their order matters.
  const getInitialPluginOrder = () =>
    getPluginsInDisplayOrder(plugins, defaultPluginIds, isOrdered).map(
      (plugin) => plugin.id
    );
  const [pluginOrder, setPluginOrder] = useState<string[]>(
    getInitialPluginOrder
  );

  const pluginsInDisplayOrder = getPluginsInDisplayOrder(
    plugins,
    selectedPluginIds,
    isOrdered,
    pluginOrder
  );
  const selectedPluginIdSet = new Set(selectedPluginIds);
  const selectedInDisplayOrder = (selectedIds: ReadonlySet<string>) =>
    pluginsInDisplayOrder
      .map((plugin) => plugin.id)
      .filter((pluginId) => selectedIds.has(pluginId));

  const setSelected = (pluginId: string, selected: boolean) => {
    const nextSelectedPluginIds = new Set(selectedPluginIdSet);

    if (selected) {
      nextSelectedPluginIds.add(pluginId);
    } else {
      nextSelectedPluginIds.delete(pluginId);
    }

    setSelectedPluginIds(selectedInDisplayOrder(nextSelectedPluginIds));
  };

  const reorder = (fromIndex: number, toIndex: number) => {
    const nextPluginOrder = moveItem(
      pluginsInDisplayOrder.map((plugin) => plugin.id),
      fromIndex,
      toIndex
    );

    setPluginOrder(nextPluginOrder);
    setSelectedPluginIds(
      nextPluginOrder.filter((pluginId) => selectedPluginIdSet.has(pluginId))
    );
  };

  const discard = () => {
    setSelectedPluginIds(defaultPluginIds);
    setPluginOrder(getInitialPluginOrder());
  };

  const isDirty = pluginIdsDiffer(
    selectedPluginIds,
    defaultPluginIds,
    isOrdered
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
            enableReordering={isOrdered}
            onReorder={reorder}
            renderItem={(plugin, dragHandle) => (
              <PluginListItem
                plugin={plugin}
                selected={selectedPluginIdSet.has(plugin.id)}
                onSelectedChange={(selected) =>
                  setSelected(plugin.id, selected)
                }
                dragHandle={dragHandle}
              />
            )}
          />
        )}
      </CardContent>
      <CardFooter className='flex gap-2'>
        <Button
          type='button'
          variant='outline'
          disabled={!isDirty || isPending}
          onClick={discard}
        >
          Discard Changes
        </Button>
        <Button
          type='button'
          variant='outline'
          disabled={!isCustomized || isPending}
          onClick={onReset}
        >
          Reset to {level === 'GLOBAL' ? 'Built-in' : 'Global'} Defaults
        </Button>
        <Button
          type='button'
          disabled={!(isDirty || !isCustomized) || isPending}
          onClick={() => onSave(selectedPluginIds)}
        >
          {isPending ? (
            <>
              <span className='sr-only'>Saving ...</span>
              <Loader2 size={16} className='mx-3 animate-spin' />
            </>
          ) : (
            'Save'
          )}
        </Button>
      </CardFooter>
    </Card>
  );
};
