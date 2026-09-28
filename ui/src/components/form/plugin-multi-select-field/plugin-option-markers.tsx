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
  useWatch,
  type Control,
  type FieldValues,
  type Path,
} from 'react-hook-form';

import type { PreconfiguredPluginDescriptor } from '@/api';
import { Badge } from '@/components/ui/badge';
import {
  getMissingRequiredOptions,
  getOptionsDifferingFromDefaults,
  type PluginConfigValues,
} from '@/helpers/plugin-options';

type PluginOptionMarkersProps<TFieldValues extends FieldValues> = {
  control: Control<TFieldValues>;
  /** The path of the plugin's configuration. */
  name: Path<TFieldValues>;
  plugin: PreconfiguredPluginDescriptor;
  /**
   * Whether the plugin is enabled. Missing and modified options are only marked for enabled
   * plugins.
   */
  enabled: boolean;
  /**
   * The number of settings shown with the options that are not plugin options, such as the
   * must-run-after list. Users cannot tell them apart from options, so they are counted too.
   */
  additionalSettingCount: number;
};

/**
 * Show how many options of a plugin can be changed, how many differ from their defaults, and
 * how many required options are not set.
 * Fixed options are not counted, because they cannot be changed.
 */
export const PluginOptionMarkers = <TFieldValues extends FieldValues>({
  control,
  name,
  plugin,
  enabled,
  additionalSettingCount,
}: PluginOptionMarkersProps<TFieldValues>) => {
  // Watch only this plugin's configuration, so typing in one plugin's options does not
  // update the markers of all other plugins.
  const pluginConfig = useWatch({ control, name }) as
    PluginConfigValues | undefined;
  const editableOptionCount =
    plugin.options.filter((option) => !option.isFixed).length +
    additionalSettingCount;
  const missingOptionCount = enabled
    ? getMissingRequiredOptions(plugin, pluginConfig).length
    : 0;
  const modifiedOptionCount = enabled
    ? getOptionsDifferingFromDefaults(plugin, pluginConfig).length
    : 0;

  return (
    <>
      {editableOptionCount > 0 && (
        <Badge variant='secondary' title='Total of editable user options.'>
          {editableOptionCount}{' '}
          {editableOptionCount === 1 ? 'option' : 'options'}
        </Badge>
      )}
      {modifiedOptionCount > 0 && (
        <Badge
          className='bg-amber-200 text-black'
          title='Options whose values differ from their defaults.'
        >
          {modifiedOptionCount} modified
        </Badge>
      )}
      {missingOptionCount > 0 && (
        <Badge
          variant='destructive'
          title='Required options that have no value.'
        >
          {missingOptionCount} required
        </Badge>
      )}
    </>
  );
};
