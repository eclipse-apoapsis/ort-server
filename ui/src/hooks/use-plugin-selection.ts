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

import type { CheckedState } from '@radix-ui/react-checkbox';
import { useState } from 'react';
import type {
  ControllerRenderProps,
  FieldPathByValue,
  FieldPathValue,
  FieldValues,
  Path,
  UseFormReturn,
} from 'react-hook-form';

import type { PreconfiguredPluginDescriptor } from '@/api';
import {
  fieldPath,
  getPluginsInDisplayOrder,
  moveItem,
} from '@/components/form/plugin-multi-select-field/utils';

type UsePluginSelectionArgs<
  TFieldValues extends FieldValues,
  TName extends FieldPathByValue<TFieldValues, Array<string>>,
> = {
  form: UseFormReturn<TFieldValues, TName>;
  /** The field holding the ids of the selected plugins. */
  field: ControllerRenderProps<TFieldValues, TName>;
  plugins: readonly PreconfiguredPluginDescriptor[];
  /** The field holding the scanner scope of each plugin, if the plugins have scopes. */
  scannerScopeName?: TName;
  /** The field holding the must-run-after list of each plugin, if the plugins have them. */
  mustRunAfterName?: TName;
  /** Store the selection in display order instead of the order in which it was made. */
  enableReordering: boolean;
  /** Show selected plugins first, in selection order, until the user reorders the list. */
  showSelectedPluginsFirst: boolean;
};

/**
 * Manage the selection of a plugin multi-select field. This is the only place that changes
 * the selection and the values that depend on it: enabling a plugin defaults its scanner
 * scope to "both", and disabling it clears its scanner scope and must-run-after list.
 */
export function usePluginSelection<
  TFieldValues extends FieldValues,
  TName extends FieldPathByValue<TFieldValues, Array<string>>,
>({
  form,
  field,
  plugins,
  scannerScopeName,
  mustRunAfterName,
  enableReordering,
  showSelectedPluginsFirst,
}: UsePluginSelectionArgs<TFieldValues, TName>) {
  const [pluginOrder, setPluginOrder] = useState<string[]>();

  const selectedPluginIds = (field.value ?? []) as string[];
  const selectedPluginIdSet = new Set(selectedPluginIds);
  const pluginsInDisplayOrder = getPluginsInDisplayOrder(
    plugins,
    selectedPluginIds,
    showSelectedPluginsFirst,
    pluginOrder
  );

  // Order the given plugin ids by their position in the list.
  const inDisplayOrder = (selectedIds: ReadonlySet<string>) =>
    pluginsInDisplayOrder
      .map((plugin) => plugin.id)
      .filter((pluginId) => selectedIds.has(pluginId));

  // Set a plugin's entry in a field that holds one value per plugin.
  const setPluginValue = (base: string, pluginId: string, value: unknown) =>
    form.setValue(
      fieldPath<TFieldValues>(base, pluginId),
      value as FieldPathValue<TFieldValues, Path<TFieldValues>>
    );

  // Give a newly enabled plugin the default scanner scope, unless it already has one.
  const applyEnabled = (pluginId: string) => {
    if (
      scannerScopeName &&
      !form.getValues(fieldPath<TFieldValues>(scannerScopeName, pluginId))
    ) {
      setPluginValue(scannerScopeName, pluginId, 'both');
    }
  };

  // Clear the values that only apply to an enabled plugin.
  const applyDisabled = (pluginId: string) => {
    if (scannerScopeName) setPluginValue(scannerScopeName, pluginId, undefined);
    if (mustRunAfterName) setPluginValue(mustRunAfterName, pluginId, undefined);
  };

  // Enable or disable one plugin, keeping the selection in list order when reordering.
  const setSelected = (pluginId: string, selected: boolean) => {
    const nextSelectedPluginIds = selected
      ? [...selectedPluginIds, pluginId]
      : selectedPluginIds.filter((id) => id !== pluginId);

    field.onChange(
      enableReordering
        ? inDisplayOrder(new Set(nextSelectedPluginIds))
        : nextSelectedPluginIds
    );

    if (selected) {
      applyEnabled(pluginId);
    } else {
      applyDisabled(pluginId);
    }
  };

  // Enable or disable all plugins at once.
  const setAllSelected = (selected: boolean) => {
    const orderedPlugins = enableReordering ? pluginsInDisplayOrder : plugins;
    const nextSelectedPluginIds = selected
      ? orderedPlugins.map((plugin) => plugin.id)
      : [];

    // TName only points at `string[]` fields, so the cast is safe. Unlike `setSelected`,
    // this uses `form.setValue`, which does not mark the field as dirty or touched.
    form.setValue(
      field.name,
      nextSelectedPluginIds as FieldPathValue<TFieldValues, TName>
    );

    plugins.forEach((plugin) => {
      if (selected) {
        applyEnabled(plugin.id);
      } else {
        applyDisabled(plugin.id);
      }
    });
  };

  // Move a plugin within the sortable list and store the selection in the new order.
  const reorder = (fromIndex: number, toIndex: number) => {
    const nextPluginOrder = moveItem(
      pluginsInDisplayOrder.map((plugin) => plugin.id),
      fromIndex,
      toIndex
    );

    setPluginOrder(nextPluginOrder);
    field.onChange(
      nextPluginOrder.filter((pluginId) => selectedPluginIdSet.has(pluginId))
    );
  };

  const allState: CheckedState = plugins.every((plugin) =>
    selectedPluginIdSet.has(plugin.id)
  )
    ? true
    : plugins.some((plugin) => selectedPluginIdSet.has(plugin.id))
      ? 'indeterminate'
      : false;

  return {
    pluginsInDisplayOrder,
    isSelected: (pluginId: string) => selectedPluginIdSet.has(pluginId),
    setSelected,
    setAllSelected,
    reorder,
    allState,
  };
}
