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

import { useQuery } from '@tanstack/react-query';

import type { PluginAvailability } from '@/api';
import { formatCount } from './overview-format';
import { installedPluginsOptions } from './overview-queries';

const availabilities: [PluginAvailability, string][] = [
  ['ENABLED', 'enabled'],
  ['RESTRICTED', 'restricted'],
  ['DISABLED', 'disabled'],
];

/**
 * How many of the installed plugins are enabled, restricted and disabled, once the plugins have
 * loaded. An availability that no plugin has is left out.
 */
export const InstalledPluginsNote = () => {
  const { data } = useQuery(installedPluginsOptions);

  if (!data) return null;

  const counts = availabilities
    .map(([availability, label]) => ({
      label,
      count: data.filter((plugin) => plugin.availability === availability)
        .length,
    }))
    .filter(({ count }) => count > 0);

  if (counts.length === 1 && counts[0]!.label === 'enabled') {
    return 'All enabled';
  }

  return counts
    .map(({ count, label }) => `${formatCount(count)} ${label}`)
    .join(', ');
};
