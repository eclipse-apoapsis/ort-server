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

import { useState } from 'react';

import type { PluginType, PreconfiguredPluginDescriptor } from '@/api';
import {
  getUnavailablePlugins,
  pluginIdsDiffer,
} from '@/components/default-plugins/utils';
import {
  getPluginsInDisplayOrder,
  moveItem,
} from '@/components/form/plugin-multi-select-field/utils';

type UseDefaultPluginsSelectionArgs = {
  /** The type of the plugins in the category. */
  pluginType: PluginType;
  /** Whether the order of the default plugins matters. */
  isOrdered: boolean;
  /** The ids of the current default plugins. */
  defaultPluginIds: readonly string[];
  /** The plugins that can be selected as default plugins. */
  availablePlugins: readonly PreconfiguredPluginDescriptor[];
};

/** Manage the unsaved selection of the default plugins of one category. */
export function useDefaultPluginsSelection({
  pluginType,
  isOrdered,
  defaultPluginIds,
  availablePlugins,
}: UseDefaultPluginsSelectionArgs) {
  // Without local changes, the current defaults are selected.
  const [draftPluginIds, setDraftPluginIds] = useState<string[]>();
  // The display order is kept apart from the local changes, so that plugins can be arranged
  // before they are selected, and do not move when they are selected.
  const [displayOrder, setDisplayOrder] = useState<string[]>();
  const selectedPluginIds = draftPluginIds ?? defaultPluginIds;
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
  // A display order that shows the selected plugins in another order than they are saved in, for
  // example after the defaults changed, is not used.
  const isDisplayOrderValid =
    displayOrder !== undefined &&
    !pluginIdsDiffer(
      displayOrder.filter((id) => selectedPluginIdSet.has(id)),
      selectedPluginIds,
      true
    );
  // Selected plugins are only shown first if their order matters.
  const pluginsInDisplayOrder = isOrdered
    ? getPluginsInDisplayOrder(
        plugins,
        selectedPluginIds,
        isDisplayOrderValid ? displayOrder : undefined
      )
    : plugins;
  const pluginOrder = pluginsInDisplayOrder.map((plugin) => plugin.id);

  // Store the selection in display order. A selection equal to the defaults is no local change.
  const change = (nextPluginOrder: string[], selected: ReadonlySet<string>) => {
    const pluginIds = nextPluginOrder.filter((id) => selected.has(id));

    setDisplayOrder(nextPluginOrder);
    setDraftPluginIds(
      pluginIdsDiffer(pluginIds, defaultPluginIds, isOrdered)
        ? pluginIds
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

  return {
    /** The available plugins, followed by the configured or selected unavailable plugins. */
    plugins,
    pluginsInDisplayOrder,
    selectedPluginIds,
    isSelected: (pluginId: string) => selectedPluginIdSet.has(pluginId),
    hasChanges: draftPluginIds !== undefined,
    setSelected,
    reorder,
    /** Drop the local changes and the display order, so that the current defaults are shown again. */
    discard: () => {
      setDraftPluginIds(undefined);
      setDisplayOrder(undefined);
    },
  };
}
