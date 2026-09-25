/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import type { ReactNode } from 'react';
import type {
  ControllerRenderProps,
  FieldPathByValue,
  FieldValues,
  UseFormReturn,
} from 'react-hook-form';

import { PreconfiguredPluginDescriptor, Secret } from '@/api';
import { FormField } from '@/components/ui/form';
import { usePluginSelection } from '@/hooks/use-plugin-selection';
import { PluginFieldFrame } from './plugin-field-frame';
import { PluginList } from './plugin-list';
import { PluginListItem } from './plugin-list-item';
import { PluginSettings } from './plugin-settings';
import { SelectAllCheckbox } from './select-all-checkbox';

type PluginMultiSelectFieldProps<
  TFieldValues extends FieldValues,
  TName extends FieldPathByValue<TFieldValues, Array<string>>,
> = {
  form: UseFormReturn<TFieldValues, TName>;
  name: TName;
  configName: TName;
  /**
   * Optional field path for a `Record<string, ScannerScope>` value. When provided,
   * a scope toggle ("Both" / "Packages only" / "Projects only") is shown next to
   * each enabled plugin so the user can control whether the scanner runs on packages,
   * projects, or both.
   */
  scannerScopeName?: TName;
  /**
   * Optional field path for a `Record<string, string[]>` value. When provided, a
   * "Must run after" multi select is shown for each enabled plugin, offering the
   * IDs of all other available plugins.
   */
  mustRunAfterName?: TName;
  label?: string;
  description?: ReactNode;
  plugins: readonly PreconfiguredPluginDescriptor[];
  secrets: readonly Secret[];
  /**
   * Enable drag-and-drop reordering for plugins. In this mode, all plugins are
   * displayed in a sortable list and selected plugins are stored in the same
   * order in which they appear in the list.
   */
  enableReordering?: boolean;
  /**
   * Show selected plugins first in their form value order before the user has
   * reordered the list. This is intended for reruns, where an existing payload
   * order should be reflected in the UI.
   */
  showSelectedPluginsFirst?: boolean;
  className?: string;
};

/**
 * A form field for enabling plugins and configuring the options of the enabled ones.
 * Optionally, each plugin also gets a scanner scope or a must-run-after list, and the
 * plugins can be reordered by drag and drop.
 */
export const PluginMultiSelectField = <
  TFieldValues extends FieldValues,
  TName extends FieldPathByValue<TFieldValues, Array<string>>,
>(
  props: PluginMultiSelectFieldProps<TFieldValues, TName>
) => (
  <FormField
    control={props.form.control}
    name={props.name}
    render={({ field }) => (
      <PluginMultiSelectFieldContent {...props} field={field} />
    )}
  />
);

/**
 * The content of the field. It is a component of its own, because the selection hook
 * needs the field, which is only available inside the `FormField`.
 */
const PluginMultiSelectFieldContent = <
  TFieldValues extends FieldValues,
  TName extends FieldPathByValue<TFieldValues, Array<string>>,
>({
  form,
  field,
  configName,
  scannerScopeName,
  mustRunAfterName,
  label,
  description,
  plugins,
  secrets,
  enableReordering = false,
  showSelectedPluginsFirst = false,
  className,
}: PluginMultiSelectFieldProps<TFieldValues, TName> & {
  field: ControllerRenderProps<TFieldValues, TName>;
}) => {
  const selection = usePluginSelection({
    form,
    field,
    plugins,
    scannerScopeName,
    mustRunAfterName,
    enableReordering,
    showSelectedPluginsFirst,
  });
  const pluginIds = plugins.map((plugin) => plugin.id);

  // Render one plugin, with its settings only while it is enabled.
  const renderItem = (
    plugin: PreconfiguredPluginDescriptor,
    dragHandle?: ReactNode
  ) => {
    const selected = selection.isSelected(plugin.id);

    return (
      <PluginListItem
        plugin={plugin}
        selected={selected}
        onSelectedChange={(checked) =>
          selection.setSelected(plugin.id, checked)
        }
        dragHandle={dragHandle}
        settings={
          selected && (
            <PluginSettings
              control={form.control}
              plugin={plugin}
              pluginIds={pluginIds}
              secrets={secrets}
              configName={configName}
              scannerScopeName={scannerScopeName}
              mustRunAfterName={mustRunAfterName}
            />
          )
        }
      />
    );
  };

  return (
    <PluginFieldFrame
      label={label}
      description={description}
      className={className}
      headerActions={
        <SelectAllCheckbox
          state={selection.allState}
          onChange={selection.setAllSelected}
        />
      }
    >
      <PluginList
        // Only a sortable list shows the display order; a static list keeps the
        // original order, even with `showSelectedPluginsFirst`.
        plugins={enableReordering ? selection.pluginsInDisplayOrder : plugins}
        enableReordering={enableReordering}
        renderItem={renderItem}
        onReorder={selection.reorder}
      />
    </PluginFieldFrame>
  );
};
