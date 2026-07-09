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

export const UNDEFINED_ENUM_VALUE = '__undefined_enum__';
export const UNDEFINED_SECRET_VALUE = '__undefined_secret__';

export function getPluginsInDisplayOrder<T extends { id: string }>(
  plugins: readonly T[],
  selectedPluginIds: readonly string[],
  showSelectedPluginsFirst: boolean,
  pluginOrder?: readonly string[]
) {
  const pluginsById = new Map(plugins.map((plugin) => [plugin.id, plugin]));
  const pluginIds = plugins.map((plugin) => plugin.id);
  const selectedPluginIdSet = new Set(selectedPluginIds);
  const selectedPluginIdsInPayloadOrder = selectedPluginIds.filter((pluginId) =>
    pluginsById.has(pluginId)
  );
  const fallbackPluginOrder = showSelectedPluginsFirst
    ? [
        ...selectedPluginIdsInPayloadOrder,
        ...pluginIds.filter((pluginId) => !selectedPluginIdSet.has(pluginId)),
      ]
    : pluginIds;
  const orderedPluginIds = pluginOrder ?? fallbackPluginOrder;
  const pluginOrderSet = new Set(orderedPluginIds);

  return [
    ...orderedPluginIds.flatMap((pluginId) => {
      const plugin = pluginsById.get(pluginId);
      return plugin ? [plugin] : [];
    }),
    ...plugins.filter((plugin) => !pluginOrderSet.has(plugin.id)),
  ];
}

export function getSecretSelectDisplayValue(
  value: string | undefined,
  isRequired: boolean
): string | undefined {
  if (value === undefined) {
    return isRequired ? undefined : UNDEFINED_SECRET_VALUE;
  }

  return value;
}

export function mapSecretSelectValue(
  value: string | undefined
): string | undefined {
  return value === UNDEFINED_SECRET_VALUE ? undefined : value;
}

export function getEnumSelectDisplayValue(
  value: string | undefined,
  isRequired: boolean
): string | undefined {
  if (value === undefined || value === '') {
    return isRequired ? undefined : UNDEFINED_ENUM_VALUE;
  }

  return value;
}

export function mapEnumSelectValue(
  value: string | undefined
): string | undefined {
  return value === UNDEFINED_ENUM_VALUE ? undefined : value;
}

export function moveItem<T>(
  items: readonly T[],
  fromIndex: number,
  toIndex: number
) {
  const newItems = [...items];
  const [removed] = newItems.splice(fromIndex, 1);

  if (removed !== undefined) {
    newItems.splice(toIndex, 0, removed);
  }

  return newItems;
}
