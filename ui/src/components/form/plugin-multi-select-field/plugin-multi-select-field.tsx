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
import {
  get,
  useFormState,
  type ControllerRenderProps,
  type FieldPathByValue,
  type FieldValues,
  type UseFormReturn,
} from 'react-hook-form';

import { PreconfiguredPluginDescriptor, Secret } from '@/api';
import { Accordion } from '@/components/ui/accordion';
import { FormField } from '@/components/ui/form';
import {
  getInitiallyExpandedPluginIds,
  type PluginConfigValues,
} from '@/helpers/plugin-options';
import { usePluginExpansion } from '@/hooks/use-plugin-expansion';
import { usePluginSelection } from '@/hooks/use-plugin-selection';
import { ExpandAllButton } from './expand-all-button';
import { PluginFieldFrame } from './plugin-field-frame';
import { PluginList } from './plugin-list';
import { PluginListItem } from './plugin-list-item';
import { PluginOptionMarkers } from './plugin-option-markers';
import { PluginSettings } from './plugin-settings';
import { SelectAllCheckbox } from './select-all-checkbox';
import { fieldPath } from './utils';

type PluginMultiSelectFieldProps<
  TFieldValues extends FieldValues,
  TName extends FieldPathByValue<TFieldValues, Array<string>>,
> = {
  form: UseFormReturn<TFieldValues, TName>;
  name: TName;
  configName: TName;
  /**
   * Optional field path for a `Record<string, ScannerScope>` value. When provided,
   * a scope toggle ("Both" / "Packages only" / "Projects only") is shown with the
   * settings of each plugin so the user can control whether the scanner runs on
   * packages, projects, or both.
   */
  scannerScopeName?: TName;
  /**
   * Optional field path for a `Record<string, string[]>` value. When provided, a
   * "Must run after" multi select is shown with the settings of each plugin, offering
   * the IDs of all other available plugins.
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
 * A form field for enabling plugins and configuring their options. The options of each
 * plugin are shown in a collapsible section. Plugins start collapsed, except enabled plugins
 * with a required option that is not set or with options that differ from their defaults.
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
    enableReordering,
    showSelectedPluginsFirst,
  });
  // A plugin has settings to show if it has options, or if the field has a scanner scope or
  // must-run-after list for it.
  const hasScopeOrMustRunAfter = Boolean(scannerScopeName || mustRunAfterName);
  const hasSettings = (plugin: PreconfiguredPluginDescriptor) =>
    plugin.options.length > 0 || hasScopeOrMustRunAfter;
  const expandableIds = plugins
    .filter((plugin) => hasSettings(plugin))
    .map((plugin) => plugin.id);

  const { submitCount, errors } = useFormState({
    control: form.control,
    name: configName,
  });
  const expansion = usePluginExpansion({
    expandableIds,
    initiallyExpandedIds: getInitiallyExpandedPluginIds(
      plugins,
      (field.value ?? []) as string[],
      form.getValues(configName) as unknown as Record<
        string,
        PluginConfigValues | undefined
      >
    ),
    submitCount,
    // The errors of the configuration are keyed by plugin id.
    idsWithErrors: Object.keys(get(errors, configName) ?? {}),
  });
  const pluginIds = plugins.map((plugin) => plugin.id);

  // Expand a plugin with settings when it is enabled, and collapse it when it is disabled.
  const setSelected = (
    plugin: PreconfiguredPluginDescriptor,
    selected: boolean
  ) => {
    selection.setSelected(plugin.id, selected);

    if (!selected || hasSettings(plugin)) {
      expansion.setExpanded(plugin.id, selected);
    }
  };

  // Enabling all plugins expands none of them, but disabling all collapses all.
  const setAllSelected = (selected: boolean) => {
    selection.setAllSelected(selected);
    if (!selected) expansion.setAllExpanded(false);
  };

  // Render one plugin, with its settings if it has any. They are only rendered while the
  // plugin is expanded.
  const renderItem = (
    plugin: PreconfiguredPluginDescriptor,
    dragHandle?: ReactNode
  ) => {
    const selected = selection.isSelected(plugin.id);

    return (
      <PluginListItem
        plugin={plugin}
        selected={selected}
        onSelectedChange={(checked) => setSelected(plugin, checked)}
        dragHandle={dragHandle}
        markers={
          <PluginOptionMarkers
            control={form.control}
            name={fieldPath<TFieldValues>(configName, plugin.id)}
            plugin={plugin}
            enabled={selected}
            additionalSettingCount={
              Number(Boolean(scannerScopeName)) +
              Number(Boolean(mustRunAfterName))
            }
          />
        }
        settings={
          hasSettings(plugin) && (
            <PluginSettings
              control={form.control}
              plugin={plugin}
              pluginIds={pluginIds}
              secrets={secrets}
              configName={configName}
              scannerScopeName={scannerScopeName}
              mustRunAfterName={mustRunAfterName}
              enabled={selected}
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
        <div className='flex items-center justify-between'>
          <SelectAllCheckbox
            state={selection.allState}
            onChange={setAllSelected}
          />
          {expandableIds.length > 0 && (
            <ExpandAllButton
              allExpanded={expansion.allExpanded}
              onChange={expansion.setAllExpanded}
            />
          )}
        </div>
      }
    >
      <Accordion
        type='multiple'
        value={expansion.expandedIds}
        onValueChange={expansion.setExpandedIds}
        className='flex flex-col gap-2'
      >
        <PluginList
          // Only a sortable list shows the display order; a static list keeps the
          // original order, even with `showSelectedPluginsFirst`.
          plugins={enableReordering ? selection.pluginsInDisplayOrder : plugins}
          enableReordering={enableReordering}
          renderItem={renderItem}
          onReorder={selection.reorder}
        />
      </Accordion>
    </PluginFieldFrame>
  );
};
