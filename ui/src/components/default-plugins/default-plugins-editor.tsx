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

import type {
  DefaultPlugins,
  DefaultPluginsCategory,
  PreconfiguredPluginDescriptor,
} from '@/api';
import { DefaultPluginsCard } from './default-plugins-card';
import { defaultPluginsCategories, type DefaultPluginsLevel } from './utils';

type DefaultPluginsEditorProps = {
  /** The level on which the defaults are edited. */
  level: DefaultPluginsLevel;
  /** The current default plugins of all categories. */
  defaults: readonly DefaultPlugins[];
  /** The plugins that can be selected as default plugins, of all plugin types. */
  availablePlugins: readonly PreconfiguredPluginDescriptor[];
  /** Whether the user can change the defaults. */
  editable: boolean;
  /** The category whose defaults are currently being changed, if any. */
  pendingCategory?: DefaultPluginsCategory;
  onSave: (category: DefaultPluginsCategory, pluginIds: string[]) => void;
  onReset: (category: DefaultPluginsCategory) => void;
};

/** Edit the default plugins of all categories. */
export const DefaultPluginsEditor = ({
  level,
  defaults,
  availablePlugins,
  editable,
  pendingCategory,
  onSave,
  onReset,
}: DefaultPluginsEditorProps) => (
  <div className='flex flex-col gap-4'>
    {defaultPluginsCategories.map(
      ({ category, pluginType, title, description, isOrdered }) => {
        const defaultsForCategory = defaults.find(
          (entry) => entry.category === category
        );

        if (!defaultsForCategory) return null;

        return (
          <DefaultPluginsCard
            // Create a new card when the defaults change, to discard the local changes.
            key={`${category}:${defaultsForCategory.source}:${defaultsForCategory.pluginIds.join(',')}`}
            title={title}
            description={description}
            pluginType={pluginType}
            isOrdered={isOrdered}
            level={level}
            defaults={defaultsForCategory}
            availablePlugins={availablePlugins.filter(
              (plugin) => plugin.type === pluginType
            )}
            editable={editable}
            isPending={pendingCategory === category}
            onSave={(pluginIds) => onSave(category, pluginIds)}
            onReset={() => onReset(category)}
          />
        );
      }
    )}
  </div>
);
