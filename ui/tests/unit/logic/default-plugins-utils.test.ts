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

import { describe, expect, it } from 'vitest';

import {
  getUnavailablePlugins,
  normalizePluginIds,
  pluginIdsDiffer,
} from '@/components/default-plugins/utils';
import { createPluginDescriptor } from '../fixtures/create-run';

describe('getUnavailablePlugins', () => {
  it('returns descriptors for the plugin ids that are not available', () => {
    const available = [createPluginDescriptor({ id: 'A', type: 'ADVISOR' })];

    const unavailable = getUnavailablePlugins('ADVISOR', ['A', 'B'], available);

    expect(unavailable).toHaveLength(1);
    expect(unavailable[0]).toMatchObject({
      id: 'B',
      type: 'ADVISOR',
      displayName: 'B',
      options: [],
    });
  });

  it('returns nothing if all plugins are available', () => {
    const available = [createPluginDescriptor({ id: 'A' })];

    expect(getUnavailablePlugins('SCANNER', ['A'], available)).toEqual([]);
  });
});

describe('normalizePluginIds', () => {
  it('maps plugin ids to the ids of the available plugins ignoring the case', () => {
    const available = [
      createPluginDescriptor({ id: 'webApp', type: 'REPORTER' }),
      createPluginDescriptor({ id: 'CycloneDX', type: 'REPORTER' }),
    ];

    expect(
      normalizePluginIds(['WebApp', 'cyclonedx', 'Removed'], available)
    ).toEqual(['webApp', 'CycloneDX', 'Removed']);
  });

  it('prefers exact matches', () => {
    const available = [
      createPluginDescriptor({ id: 'webApp', type: 'REPORTER' }),
      createPluginDescriptor({ id: 'WebApp', type: 'REPORTER' }),
    ];

    expect(normalizePluginIds(['WebApp'], available)).toEqual(['WebApp']);
  });

  it('removes duplicates', () => {
    const available = [
      createPluginDescriptor({ id: 'webApp', type: 'REPORTER' }),
    ];

    expect(normalizePluginIds(['WebApp', 'webApp'], available)).toEqual([
      'webApp',
    ]);
  });
});

describe('pluginIdsDiffer', () => {
  it('ignores the order if it does not matter', () => {
    expect(pluginIdsDiffer(['A', 'B'], ['B', 'A'], false)).toBe(false);
  });

  it('takes the order into account if it matters', () => {
    expect(pluginIdsDiffer(['A', 'B'], ['B', 'A'], true)).toBe(true);
    expect(pluginIdsDiffer(['A', 'B'], ['A', 'B'], true)).toBe(false);
  });

  it('detects different plugins', () => {
    expect(pluginIdsDiffer(['A'], ['A', 'B'], false)).toBe(true);
    expect(pluginIdsDiffer(['A', 'C'], ['A', 'B'], false)).toBe(true);
  });
});
