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
import {
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { DefaultPluginsCard } from './default-plugins-card';
import { defaultPluginsCategories, type DefaultPluginsLevel } from './utils';

type DefaultPluginsPageProps = {
  /** The level on which the defaults are edited. */
  level: DefaultPluginsLevel;
  /** A description of the defaults shown at the top of the page. */
  description: string;
  /** The current default plugins of all categories. */
  defaults: readonly DefaultPlugins[];
  /** The plugins that can be selected as default plugins, of all plugin types. */
  availablePlugins: readonly PreconfiguredPluginDescriptor[];
  setDefaults: (
    category: DefaultPluginsCategory,
    pluginIds: string[]
  ) => Promise<unknown>;
  resetDefaults: (category: DefaultPluginsCategory) => Promise<unknown>;
};

/** A page to edit the default plugins of all categories on one level, with one card per category. */
export const DefaultPluginsPage = ({
  level,
  description,
  defaults,
  availablePlugins,
  setDefaults,
  resetDefaults,
}: DefaultPluginsPageProps) => (
  <div className='flex flex-col gap-4'>
    <Card>
      <CardHeader>
        <CardTitle>Default Plugins</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
    </Card>
    {defaultPluginsCategories.map((info) => {
      const defaultsForCategory = defaults.find(
        (entry) => entry.category === info.category
      );

      if (!defaultsForCategory) return null;

      return (
        <DefaultPluginsCard
          key={info.category}
          category={info}
          level={level}
          defaults={defaultsForCategory}
          availablePlugins={availablePlugins.filter(
            (plugin) => plugin.type === info.pluginType
          )}
          setDefaults={(pluginIds) => setDefaults(info.category, pluginIds)}
          resetDefaults={() => resetDefaults(info.category)}
        />
      );
    })}
  </div>
);
