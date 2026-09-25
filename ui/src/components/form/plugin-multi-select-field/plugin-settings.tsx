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

import type { Control, FieldValues, Path } from 'react-hook-form';

import type { PreconfiguredPluginDescriptor, Secret } from '@/api';
import { MustRunAfterField } from './must-run-after-field';
import { PluginOptionField } from './plugin-option-field';
import { ScannerScopeToggle } from './scanner-scope-toggle';
import { fieldPath, optionFieldPath } from './utils';

type PluginSettingsProps<TFieldValues extends FieldValues> = {
  control: Control<TFieldValues>;
  plugin: PreconfiguredPluginDescriptor;
  /** The ids of all plugins in the field, offered by the must-run-after selector. */
  pluginIds: readonly string[];
  secrets: readonly Secret[];
  /** The path of the field holding the option values of all plugins. */
  configName: string;
  /** The path of the field holding the scanner scopes, if the field has scopes. */
  scannerScopeName?: string;
  /** The path of the field holding the must-run-after lists, if the field has them. */
  mustRunAfterName?: string;
};

/** The settings of one enabled plugin: its scanner scope, its options, and must-run-after. */
export const PluginSettings = <TFieldValues extends FieldValues>({
  control,
  plugin,
  pluginIds,
  secrets,
  configName,
  scannerScopeName,
  mustRunAfterName,
}: PluginSettingsProps<TFieldValues>) => (
  <>
    {scannerScopeName && (
      <ScannerScopeToggle
        control={control}
        name={fieldPath<TFieldValues>(scannerScopeName, plugin.id)}
      />
    )}
    {plugin.options.map((option) => (
      <PluginOptionField
        key={option.name}
        control={control}
        name={
          optionFieldPath(configName, plugin.id, option) as Path<TFieldValues>
        }
        option={option}
        secrets={secrets}
      />
    ))}
    {mustRunAfterName && (
      <MustRunAfterField
        control={control}
        name={fieldPath<TFieldValues>(mustRunAfterName, plugin.id)}
        candidateIds={pluginIds.filter((pluginId) => pluginId !== plugin.id)}
      />
    )}
  </>
);
