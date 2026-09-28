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

import type { PreconfiguredPluginOption } from '@/api';
import { ADMIN_SECRET_VALUE } from '@/components/form/plugin-multi-select-field';
import {
  differsFromDefaults,
  getInitiallyExpandedPluginIds,
  getMissingRequiredOptions,
  getOptionsDifferingFromDefaults,
  isOptionValueMissing,
  optionDiffersFromDefault,
  optionValueDiffersFromDefault,
} from '@/helpers/plugin-options';
import { getPluginDefaultValues } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components/plugin-utils';
import { createPluginDescriptor } from '../fixtures/create-run';

const createOption = (
  overrides: Partial<PreconfiguredPluginOption> = {}
): PreconfiguredPluginOption => ({
  name: 'option',
  description: 'An option.',
  type: 'STRING',
  isFixed: false,
  isNullable: false,
  isRequired: false,
  ...overrides,
});

describe('isOptionValueMissing', () => {
  it.each([undefined, null, '', []])('treats %j as missing', (value) => {
    expect(isOptionValueMissing(value)).toBe(true);
  });

  it.each([false, 'value', ['entry'], '0'])('treats %j as set', (value) => {
    expect(isOptionValueMissing(value)).toBe(false);
  });
});

describe('getMissingRequiredOptions', () => {
  const plugin = createPluginDescriptor({
    options: [
      createOption({ name: 'url', isRequired: true }),
      createOption({ name: 'token', type: 'SECRET', isRequired: true }),
      createOption({ name: 'comment' }),
    ],
  });

  it('returns the required options without a value', () => {
    const missing = getMissingRequiredOptions(plugin, {
      options: { url: '' },
      secrets: {},
    });

    expect(missing.map((option) => option.name)).toEqual(['url', 'token']);
  });

  it('looks up secret options under secrets and other options under options', () => {
    const missing = getMissingRequiredOptions(plugin, {
      options: { url: 'https://example.org', token: 'misplaced' },
      secrets: { url: 'misplaced', token: 'my-secret' },
    });

    expect(missing).toEqual([]);
  });

  it('ignores optional options', () => {
    const missing = getMissingRequiredOptions(plugin, {
      options: { url: 'https://example.org' },
      secrets: { token: 'my-secret' },
    });

    expect(missing).toEqual([]);
  });

  it('treats an undefined configuration as missing all required options', () => {
    const missing = getMissingRequiredOptions(plugin, undefined);

    expect(missing.map((option) => option.name)).toEqual(['url', 'token']);
  });
});

describe('optionValueDiffersFromDefault', () => {
  it('compares booleans with a string default', () => {
    const option = createOption({ type: 'BOOLEAN', defaultValue: 'true' });

    expect(optionValueDiffersFromDefault(option, true)).toBe(false);
    expect(optionValueDiffersFromDefault(option, 'true')).toBe(false);
    expect(optionValueDiffersFromDefault(option, false)).toBe(true);
  });

  it('compares lists with a comma-separated default, respecting the order', () => {
    const option = createOption({ type: 'STRING_LIST', defaultValue: 'a, b' });

    expect(optionValueDiffersFromDefault(option, ['a', 'b'])).toBe(false);
    expect(optionValueDiffersFromDefault(option, ['b', 'a'])).toBe(true);
    expect(optionValueDiffersFromDefault(option, ['a'])).toBe(true);
  });

  it('compares numbers in their string representation', () => {
    const option = createOption({ type: 'INTEGER', defaultValue: '5' });

    expect(optionValueDiffersFromDefault(option, 5)).toBe(false);
    expect(optionValueDiffersFromDefault(option, '6')).toBe(true);
  });

  it('returns false for an option without default', () => {
    const option = createOption();

    expect(optionValueDiffersFromDefault(option, 'value')).toBe(false);
  });
});

describe('differsFromDefaults', () => {
  const plugin = createPluginDescriptor({
    options: [
      createOption({ name: 'flag', type: 'BOOLEAN', defaultValue: 'false' }),
      createOption({ name: 'list', type: 'STRING_LIST', defaultValue: 'a,b' }),
      createOption({ name: 'free' }),
      createOption({
        name: 'fixed',
        defaultValue: 'admin',
        isFixed: true,
      }),
      createOption({ name: 'token', type: 'SECRET' }),
    ],
  });

  it('is false for the default values of the form', () => {
    const defaults = getPluginDefaultValues([plugin]);

    expect(differsFromDefaults(plugin, defaults[plugin.id])).toBe(false);
  });

  it('is false for an undefined configuration', () => {
    expect(differsFromDefaults(plugin, undefined)).toBe(false);
  });

  it('is true for an option that differs from its default', () => {
    expect(
      differsFromDefaults(plugin, { options: { flag: true, list: ['a', 'b'] } })
    ).toBe(true);
  });

  it('is true for a set option without default', () => {
    expect(differsFromDefaults(plugin, { options: { free: 'value' } })).toBe(
      true
    );
  });

  it('is false for an option without a value', () => {
    expect(
      differsFromDefaults(plugin, { options: { free: undefined, list: null } })
    ).toBe(false);
  });

  it('is true for an option cleared to an empty string or list', () => {
    expect(differsFromDefaults(plugin, { options: { free: '' } })).toBe(true);
    expect(differsFromDefaults(plugin, { options: { list: [] } })).toBe(true);
  });

  it('is false when only a fixed option differs', () => {
    expect(differsFromDefaults(plugin, { options: { fixed: 'other' } })).toBe(
      false
    );
  });

  it('is true for a secret option naming a secret', () => {
    expect(
      differsFromDefaults(plugin, { secrets: { token: 'my-secret' } })
    ).toBe(true);
  });

  it('is false for a blank secret or the admin-provided secret placeholder', () => {
    expect(differsFromDefaults(plugin, { secrets: { token: ' ' } })).toBe(
      false
    );
    expect(
      differsFromDefaults(plugin, { secrets: { token: ADMIN_SECRET_VALUE } })
    ).toBe(false);
  });
});

describe('optionDiffersFromDefault', () => {
  it('is false for a fixed option', () => {
    const option = createOption({ defaultValue: 'admin', isFixed: true });

    expect(optionDiffersFromDefault(option, 'other')).toBe(false);
  });

  it('is true for a value other than the default', () => {
    const option = createOption({ type: 'INTEGER', defaultValue: '1' });

    expect(optionDiffersFromDefault(option, '2')).toBe(true);
    expect(optionDiffersFromDefault(option, '1')).toBe(false);
  });

  it('is false for an option without a value', () => {
    const option = createOption({ defaultValue: 'value' });

    expect(optionDiffersFromDefault(option, undefined)).toBe(false);
    expect(optionDiffersFromDefault(option, null)).toBe(false);
  });

  it('is true for a value cleared from a default that is not empty', () => {
    expect(
      optionDiffersFromDefault(createOption({ defaultValue: 'value' }), '')
    ).toBe(true);
    expect(
      optionDiffersFromDefault(
        createOption({ type: 'STRING_LIST', defaultValue: 'a,b' }),
        []
      )
    ).toBe(true);
  });
});

describe('getOptionsDifferingFromDefaults', () => {
  it('returns only the options that differ from their defaults', () => {
    const plugin = createPluginDescriptor({
      options: [
        createOption({ name: 'flag', type: 'BOOLEAN', defaultValue: 'false' }),
        createOption({ name: 'free' }),
        createOption({ name: 'unchanged', defaultValue: 'value' }),
        createOption({ name: 'token', type: 'SECRET' }),
      ],
    });

    const options = getOptionsDifferingFromDefaults(plugin, {
      options: { flag: true, free: 'value', unchanged: 'value' },
      secrets: { token: 'my-secret' },
    });

    expect(options.map((option) => option.name)).toEqual([
      'flag',
      'free',
      'token',
    ]);
  });
});

describe('getInitiallyExpandedPluginIds', () => {
  const requiredPlugin = createPluginDescriptor({
    id: 'Required',
    options: [createOption({ name: 'url', isRequired: true })],
  });
  const defaultPlugin = createPluginDescriptor({
    id: 'Default',
    options: [
      createOption({ name: 'flag', type: 'BOOLEAN', defaultValue: 'false' }),
    ],
  });
  const plainPlugin = createPluginDescriptor({ id: 'Plain', options: [] });
  const plugins = [requiredPlugin, defaultPlugin, plainPlugin];

  it('includes an enabled plugin with a missing required option', () => {
    expect(getInitiallyExpandedPluginIds(plugins, ['Required'], {})).toEqual([
      'Required',
    ]);
  });

  it('includes an enabled plugin whose options differ from the defaults', () => {
    expect(
      getInitiallyExpandedPluginIds(plugins, ['Default'], {
        Default: { options: { flag: true } },
      })
    ).toEqual(['Default']);
  });

  it('excludes an enabled plugin with default options', () => {
    expect(
      getInitiallyExpandedPluginIds(plugins, ['Default'], {
        Default: { options: { flag: false } },
      })
    ).toEqual([]);
  });

  it('excludes a plugin that is not enabled, even with a missing required option', () => {
    expect(getInitiallyExpandedPluginIds(plugins, [], {})).toEqual([]);
  });

  it('excludes a plugin without options', () => {
    expect(getInitiallyExpandedPluginIds(plugins, ['Plain'], {})).toEqual([]);
  });
});
