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

// @vitest-environment jsdom

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FormProvider, useForm, type UseFormReturn } from 'react-hook-form';
import { describe, expect, it } from 'vitest';

import type { PreconfiguredPluginDescriptor } from '@/api';
import { PluginMultiSelectField } from '@/components/form/plugin-multi-select-field';
import { createPluginDescriptor } from '../fixtures/create-run';

type TwoFieldsFormValues = {
  first: string[];
  firstConfig: Record<string, unknown>;
  second: string[];
  secondConfig: Record<string, unknown>;
};

const firstPlugin = createPluginDescriptor({
  id: 'FirstPlugin',
  displayName: 'First Plugin',
});
const secondPlugin = createPluginDescriptor({
  id: 'SecondPlugin',
  displayName: 'Second Plugin',
});

/** Render two plugin fields in one form, as the analyzer and reporter sections do. */
const renderTwoFields = () => {
  let form: UseFormReturn<TwoFieldsFormValues> | undefined;

  const Harness = () => {
    form = useForm<TwoFieldsFormValues>({
      defaultValues: {
        first: [],
        firstConfig: {},
        second: [],
        secondConfig: {},
      },
    });

    return (
      <FormProvider {...form}>
        <PluginMultiSelectField
          form={form}
          name='first'
          configName='firstConfig'
          label='First field'
          plugins={[firstPlugin]}
          secrets={[]}
        />
        <PluginMultiSelectField
          form={form}
          name='second'
          configName='secondConfig'
          label='Second field'
          plugins={[secondPlugin]}
          secrets={[]}
        />
      </FormProvider>
    );
  };

  render(<Harness />);

  return () => form!.getValues();
};

type FieldFormValues = {
  plugins: string[];
  config: Record<string, unknown>;
  scopes: Record<string, unknown>;
  mustRunAfter: Record<string, unknown>;
};

const pluginA = createPluginDescriptor({ id: 'A', displayName: 'Plugin A' });
const pluginB = createPluginDescriptor({ id: 'B', displayName: 'Plugin B' });
const pluginC = createPluginDescriptor({ id: 'C', displayName: 'Plugin C' });

type FieldOptions = {
  plugins?: PreconfiguredPluginDescriptor[];
  defaultValues?: Partial<FieldFormValues>;
  withScannerScope?: boolean;
  withMustRunAfter?: boolean;
  enableReordering?: boolean;
  showSelectedPluginsFirst?: boolean;
};

/** Render a single plugin field and return a function that reads the form values. */
const renderField = ({
  plugins = [pluginA, pluginB, pluginC],
  defaultValues = {},
  withScannerScope = false,
  withMustRunAfter = false,
  enableReordering = false,
  showSelectedPluginsFirst = false,
}: FieldOptions = {}) => {
  let form: UseFormReturn<FieldFormValues> | undefined;

  const Harness = () => {
    form = useForm<FieldFormValues>({
      defaultValues: {
        plugins: [],
        config: {},
        scopes: {},
        mustRunAfter: {},
        ...defaultValues,
      },
    });

    return (
      <FormProvider {...form}>
        <PluginMultiSelectField
          form={form}
          name='plugins'
          configName='config'
          scannerScopeName={withScannerScope ? 'scopes' : undefined}
          mustRunAfterName={withMustRunAfter ? 'mustRunAfter' : undefined}
          label='Plugins'
          plugins={plugins}
          secrets={[]}
          enableReordering={enableReordering}
          showSelectedPluginsFirst={showSelectedPluginsFirst}
        />
      </FormProvider>
    );
  };

  render(<Harness />);

  return () => form!.getValues();
};

const pluginCheckbox = (displayName: string) =>
  screen.getByRole('checkbox', { name: displayName });

const selectAllCheckbox = () =>
  screen.getByRole('checkbox', { name: 'Enable/disable all' });

/** The display names of the plugins in the order in which they are rendered. */
const renderedPluginNames = () =>
  screen
    .getAllByText(/^Plugin [ABC]$/)
    .map((pluginName) => pluginName.textContent);

describe('PluginMultiSelectField', () => {
  it('toggles only its own plugins when the select-all label is clicked', async () => {
    const user = userEvent.setup();
    const getValues = renderTwoFields();

    const labels = screen.getAllByText('Enable/disable all');

    await user.click(labels[1]!);

    expect(getValues().first).toEqual([]);
    expect(getValues().second).toEqual(['SecondPlugin']);

    await user.click(labels[0]!);

    expect(getValues().first).toEqual(['FirstPlugin']);
    expect(getValues().second).toEqual(['SecondPlugin']);
  });

  it('adds a plugin to the selection when it is enabled and removes it when disabled', async () => {
    const user = userEvent.setup();
    const getValues = renderField();

    await user.click(pluginCheckbox('Plugin B'));
    expect(getValues().plugins).toEqual(['B']);

    await user.click(pluginCheckbox('Plugin B'));
    expect(getValues().plugins).toEqual([]);
  });

  it('toggles a plugin when its name is clicked', async () => {
    const user = userEvent.setup();
    const getValues = renderField();

    await user.click(screen.getByText('Plugin A'));

    expect(getValues().plugins).toEqual(['A']);
  });

  it('stores the selection in click order without reordering', async () => {
    const user = userEvent.setup();
    const getValues = renderField();

    await user.click(pluginCheckbox('Plugin C'));
    await user.click(pluginCheckbox('Plugin A'));

    expect(getValues().plugins).toEqual(['C', 'A']);
  });

  it('stores the selection in display order with reordering', async () => {
    const user = userEvent.setup();
    const getValues = renderField({ enableReordering: true });

    await user.click(pluginCheckbox('Plugin C'));
    await user.click(pluginCheckbox('Plugin A'));

    expect(getValues().plugins).toEqual(['A', 'C']);
  });

  describe('scanner scope', () => {
    it('defaults the scope to both when a plugin is enabled', async () => {
      const user = userEvent.setup();
      const getValues = renderField({ withScannerScope: true });

      await user.click(pluginCheckbox('Plugin A'));

      expect(getValues().scopes).toEqual({ A: 'both' });
      expect(screen.getByRole('radio', { name: 'Both' })).toBeChecked();
    });

    it('keeps an existing scope when a plugin is enabled', async () => {
      const user = userEvent.setup();
      const getValues = renderField({
        withScannerScope: true,
        defaultValues: { scopes: { A: 'packages' } },
      });

      await user.click(pluginCheckbox('Plugin A'));

      expect(getValues().scopes).toEqual({ A: 'packages' });
    });

    it('clears the scope when a plugin is disabled', async () => {
      const user = userEvent.setup();
      const getValues = renderField({
        withScannerScope: true,
        defaultValues: { plugins: ['A'], scopes: { A: 'projects' } },
      });

      await user.click(pluginCheckbox('Plugin A'));

      expect(getValues().scopes.A).toBeUndefined();
    });
  });

  it('clears the must-run-after entry when a plugin is disabled', async () => {
    const user = userEvent.setup();
    const getValues = renderField({
      withMustRunAfter: true,
      defaultValues: { plugins: ['A'], mustRunAfter: { A: ['B'] } },
    });

    expect(screen.getByText('Must run after')).toBeInTheDocument();

    await user.click(pluginCheckbox('Plugin A'));

    expect(getValues().mustRunAfter.A).toBeUndefined();
    expect(screen.queryByText('Must run after')).not.toBeInTheDocument();
  });

  describe('select-all checkbox', () => {
    it('is indeterminate when some plugins are enabled', () => {
      renderField({ defaultValues: { plugins: ['A'] } });

      expect(selectAllCheckbox()).toHaveAttribute('aria-checked', 'mixed');
    });

    it('is checked when all plugins are enabled', () => {
      renderField({ defaultValues: { plugins: ['A', 'B', 'C'] } });

      expect(selectAllCheckbox()).toBeChecked();
    });

    it('enables all plugins and sets missing scopes', async () => {
      const user = userEvent.setup();
      const getValues = renderField({
        withScannerScope: true,
        defaultValues: { scopes: { B: 'packages' } },
      });

      await user.click(selectAllCheckbox());

      expect(getValues().plugins).toEqual(['A', 'B', 'C']);
      expect(getValues().scopes).toEqual({
        A: 'both',
        B: 'packages',
        C: 'both',
      });
    });

    it('enables all plugins in display order with reordering', async () => {
      const user = userEvent.setup();
      const getValues = renderField({
        enableReordering: true,
        showSelectedPluginsFirst: true,
        defaultValues: { plugins: ['C'] },
      });

      await user.click(selectAllCheckbox());

      expect(getValues().plugins).toEqual(['C', 'A', 'B']);
    });

    it('disables all plugins and clears scopes and must-run-after entries', async () => {
      const user = userEvent.setup();
      const getValues = renderField({
        withScannerScope: true,
        withMustRunAfter: true,
        defaultValues: {
          plugins: ['A', 'B', 'C'],
          scopes: { A: 'both', B: 'packages', C: 'projects' },
          mustRunAfter: { A: ['B'] },
        },
      });

      await user.click(selectAllCheckbox());

      expect(getValues().plugins).toEqual([]);
      expect(getValues().scopes).toEqual({
        A: undefined,
        B: undefined,
        C: undefined,
      });
      expect(getValues().mustRunAfter.A).toBeUndefined();
    });
  });

  describe('display order', () => {
    it('renders selected plugins first in payload order with reordering', () => {
      renderField({
        enableReordering: true,
        showSelectedPluginsFirst: true,
        defaultValues: { plugins: ['C', 'A'] },
      });

      expect(renderedPluginNames()).toEqual([
        'Plugin C',
        'Plugin A',
        'Plugin B',
      ]);
    });

    it('keeps the original order without reordering', () => {
      renderField({
        showSelectedPluginsFirst: true,
        defaultValues: { plugins: ['C', 'A'] },
      });

      expect(renderedPluginNames()).toEqual([
        'Plugin A',
        'Plugin B',
        'Plugin C',
      ]);
    });

    it('renders a drag handle for each plugin with reordering', () => {
      renderField({ enableReordering: true });

      expect(
        screen.getByRole('button', { name: 'Reorder A' })
      ).toBeInTheDocument();
      expect(
        screen.getByRole('button', { name: 'Reorder B' })
      ).toBeInTheDocument();
      expect(
        screen.getByRole('button', { name: 'Reorder C' })
      ).toBeInTheDocument();
    });
  });

  describe('plugin options', () => {
    const pluginWithOptions = createPluginDescriptor({
      id: 'WithOptions',
      displayName: 'Plugin with options',
      options: [
        {
          name: 'url',
          type: 'STRING',
          description: 'The URL to use.',
          isFixed: false,
          isNullable: false,
          isRequired: false,
        },
        {
          name: 'token',
          type: 'STRING',
          description: 'The fixed token.',
          defaultValue: 'secret-token',
          isFixed: true,
          isNullable: false,
          isRequired: true,
        },
      ],
    });

    it('renders option fields only for enabled plugins', async () => {
      const user = userEvent.setup();
      renderField({ plugins: [pluginWithOptions] });

      expect(screen.queryByText('The URL to use.')).not.toBeInTheDocument();

      await user.click(pluginCheckbox('Plugin with options'));

      expect(screen.getByText('The URL to use.')).toBeInTheDocument();
    });

    it('disables a fixed option and shows the administrator notice', () => {
      renderField({
        plugins: [pluginWithOptions],
        defaultValues: {
          plugins: ['WithOptions'],
          config: {
            WithOptions: { options: { token: 'secret-token' } },
          },
        },
      });

      expect(screen.getByDisplayValue('secret-token')).toBeDisabled();
      expect(
        screen.getByText(
          'This option is set by an administrator and cannot be changed.'
        )
      ).toBeInTheDocument();
      expect(screen.getByPlaceholderText('(optional)')).toBeEnabled();
    });
  });
});
