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

import { describe, expect, it } from 'vitest';

import {
  getPluginsInDisplayOrder,
  getSecretSelectDisplayValue,
  mapSecretSelectValue,
  moveItem,
  UNDEFINED_SECRET_VALUE,
} from '@/components/form/plugin-multi-select-field-utils';

describe('getPluginsInDisplayOrder', () => {
  const plugins = [
    { id: 'ClearlyDefined' },
    { id: 'DefaultFile' },
    { id: 'OrtConfig' },
  ];

  it('keeps plugins in their original order by default', () => {
    expect(
      getPluginsInDisplayOrder(
        plugins,
        ['DefaultFile', 'ClearlyDefined'],
        false
      ).map((plugin) => plugin.id)
    ).toEqual(['ClearlyDefined', 'DefaultFile', 'OrtConfig']);
  });

  it('shows enabled plugins first in payload order for an existing selection', () => {
    expect(
      getPluginsInDisplayOrder(
        plugins,
        ['DefaultFile', 'ClearlyDefined'],
        true
      ).map((plugin) => plugin.id)
    ).toEqual(['DefaultFile', 'ClearlyDefined', 'OrtConfig']);
  });

  it('uses the explicitly sorted plugin order when set', () => {
    expect(
      getPluginsInDisplayOrder(
        plugins,
        ['DefaultFile', 'ClearlyDefined'],
        false,
        ['OrtConfig', 'DefaultFile', 'ClearlyDefined']
      ).map((plugin) => plugin.id)
    ).toEqual(['OrtConfig', 'DefaultFile', 'ClearlyDefined']);
  });
});

describe('getSecretSelectDisplayValue', () => {
  it('keeps actual secret names unchanged', () => {
    expect(getSecretSelectDisplayValue('apiKeySCANOSS', false)).toBe(
      'apiKeySCANOSS'
    );
  });

  it('keeps undefined for required fields so the placeholder is shown', () => {
    expect(getSecretSelectDisplayValue(undefined, true)).toBeUndefined();
  });

  it('maps undefined to the explicit not defined option for optional fields', () => {
    expect(getSecretSelectDisplayValue(undefined, false)).toBe(
      UNDEFINED_SECRET_VALUE
    );
  });
});

describe('mapSecretSelectValue', () => {
  it('keeps actual secret names unchanged', () => {
    expect(mapSecretSelectValue('apiKeySCANOSS')).toBe('apiKeySCANOSS');
  });

  it('maps the explicit not defined option to undefined', () => {
    expect(
      mapSecretSelectValue(getSecretSelectDisplayValue(undefined, false))
    ).toBeUndefined();
  });
});

describe('moveItem', () => {
  it('moves an item from one index to another', () => {
    expect(moveItem(['File', 'ClearlyDefined', 'OrtConfig'], 0, 2)).toEqual([
      'ClearlyDefined',
      'OrtConfig',
      'File',
    ]);
  });
});
