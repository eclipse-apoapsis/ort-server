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
import {
  FormProvider,
  useForm,
  type FieldErrors,
  type Resolver,
  type UseFormReturn,
} from 'react-hook-form';
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
  resolver?: Resolver<FieldFormValues>;
};

/** Render a single plugin field and return a function that reads the form values. */
const renderField = ({
  plugins = [pluginA, pluginB, pluginC],
  defaultValues = {},
  withScannerScope = false,
  withMustRunAfter = false,
  enableReordering = false,
  showSelectedPluginsFirst = false,
  resolver,
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
      resolver,
    });

    return (
      <FormProvider {...form}>
        <form onSubmit={form.handleSubmit(() => {})}>
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
          <button type='submit'>Submit</button>
        </form>
      </FormProvider>
    );
  };

  render(<Harness />);

  return () => form!.getValues();
};

const pluginCheckbox = (displayName: string) =>
  screen.getByRole('checkbox', { name: `Enable ${displayName}` });

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

    it('keeps the scope when a plugin is disabled', async () => {
      const user = userEvent.setup();
      const getValues = renderField({
        withScannerScope: true,
        defaultValues: { plugins: ['A'], scopes: { A: 'projects' } },
      });

      await user.click(pluginCheckbox('Plugin A'));

      expect(getValues().scopes.A).toBe('projects');
    });
  });

  it('keeps the must-run-after entry when a plugin is disabled', async () => {
    const user = userEvent.setup();
    const getValues = renderField({
      withMustRunAfter: true,
      defaultValues: { plugins: ['A'], mustRunAfter: { A: ['B'] } },
    });

    await user.click(pluginCheckbox('Plugin A'));

    expect(getValues().mustRunAfter.A).toEqual(['B']);
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

    it('disables all plugins and keeps scopes and must-run-after entries', async () => {
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
        A: 'both',
        B: 'packages',
        C: 'projects',
      });
      expect(getValues().mustRunAfter.A).toEqual(['B']);
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

    it('disables a fixed option and shows the administrator notice', async () => {
      const user = userEvent.setup();
      renderField({
        plugins: [pluginWithOptions],
        defaultValues: {
          plugins: ['WithOptions'],
          config: {
            WithOptions: { options: { token: 'secret-token' } },
          },
        },
      });

      await user.click(
        screen.getByRole('button', { name: /^Plugin with options/ })
      );

      expect(screen.getByDisplayValue('secret-token')).toBeDisabled();
      expect(
        screen.getByText(
          'This option is set by an administrator and cannot be changed.'
        )
      ).toBeInTheDocument();
      expect(screen.getByPlaceholderText('(optional)')).toBeEnabled();
    });
  });

  describe('collapsing options', () => {
    const requiredOption = {
      name: 'url',
      type: 'STRING' as const,
      description: 'The URL to use.',
      isFixed: false,
      isNullable: false,
      isRequired: true,
    };
    const flagOption = {
      name: 'flag',
      type: 'BOOLEAN' as const,
      description: 'A flag.',
      defaultValue: 'false',
      isFixed: false,
      isNullable: false,
      isRequired: false,
    };
    const fixedOption = {
      name: 'token',
      type: 'STRING' as const,
      description: 'The fixed token.',
      defaultValue: 'secret-token',
      isFixed: true,
      isNullable: false,
      isRequired: false,
    };
    const requiredPlugin = createPluginDescriptor({
      id: 'Required',
      displayName: 'Required Plugin',
      options: [requiredOption],
    });
    const flagPlugin = createPluginDescriptor({
      id: 'Flag',
      displayName: 'Flag Plugin',
      options: [flagOption],
    });
    const fixedPlugin = createPluginDescriptor({
      id: 'Fixed',
      displayName: 'Fixed Plugin',
      options: [fixedOption],
    });
    const plainPlugin = createPluginDescriptor({
      id: 'Plain',
      displayName: 'Plain Plugin',
    });

    const enableCheckbox = (displayName: string) =>
      screen.getByRole('checkbox', { name: `Enable ${displayName}` });

    const trigger = (displayName: string) =>
      screen.getByRole('button', { name: new RegExp(`^${displayName}`) });

    const expandAllButton = () =>
      screen.getByRole('button', { name: /^(Expand|Collapse) all$/ });

    it('renders a collapsed trigger only for plugins with options', () => {
      renderField({
        plugins: [flagPlugin, plainPlugin],
      });

      expect(trigger('Flag Plugin')).toHaveAttribute('aria-expanded', 'false');
      expect(screen.queryByText('A flag.')).not.toBeInTheDocument();
      expect(
        screen.queryByRole('button', { name: /^Plain Plugin/ })
      ).not.toBeInTheDocument();
      expect(screen.getByText('Plain Plugin')).toBeInTheDocument();
    });

    it('expands and collapses the options without enabling the plugin', async () => {
      const user = userEvent.setup();
      const values = renderField({
        plugins: [flagPlugin],
      });

      await user.click(trigger('Flag Plugin'));

      expect(screen.getByText('A flag.')).toBeInTheDocument();
      expect(values().plugins).toEqual([]);

      await user.click(trigger('Flag Plugin'));

      expect(screen.queryByText('A flag.')).not.toBeInTheDocument();
    });

    it('expands a plugin when it is enabled and collapses it when disabled', async () => {
      const user = userEvent.setup();
      const values = renderField({
        plugins: [flagPlugin],
      });

      await user.click(enableCheckbox('Flag Plugin'));

      expect(values().plugins).toEqual(['Flag']);
      expect(screen.getByText('A flag.')).toBeInTheDocument();

      await user.click(enableCheckbox('Flag Plugin'));

      expect(values().plugins).toEqual([]);
      expect(screen.queryByText('A flag.')).not.toBeInTheDocument();
    });

    it('expands no plugin when all are enabled, but collapses all when all are disabled', async () => {
      const user = userEvent.setup();
      renderField({
        plugins: [flagPlugin, fixedPlugin],
      });

      await user.click(selectAllCheckbox());

      expect(trigger('Flag Plugin')).toHaveAttribute('aria-expanded', 'false');

      await user.click(trigger('Flag Plugin'));
      await user.click(selectAllCheckbox());

      expect(trigger('Flag Plugin')).toHaveAttribute('aria-expanded', 'false');
    });

    it('shows the scope and must-run-after of a plugin that is not enabled', async () => {
      const user = userEvent.setup();
      renderField({
        plugins: [flagPlugin, plainPlugin],
        withScannerScope: true,
        withMustRunAfter: true,
      });

      await user.click(trigger('Flag Plugin'));

      expect(screen.getByText('A flag.')).toBeInTheDocument();
      expect(screen.getByText('Must run after')).toBeInTheDocument();
      expect(
        screen.getByRole('radio', { name: 'Packages only' })
      ).toBeInTheDocument();
    });

    it('makes a plugin without options collapsible for its must-run-after list', async () => {
      const user = userEvent.setup();
      renderField({
        plugins: [plainPlugin, flagPlugin],
        withMustRunAfter: true,
      });

      expect(trigger('Plain Plugin')).toHaveAttribute('aria-expanded', 'false');

      await user.click(enableCheckbox('Plain Plugin'));

      expect(trigger('Plain Plugin')).toHaveAttribute('aria-expanded', 'true');
      expect(screen.getByText('Must run after')).toBeInTheDocument();

      await user.click(trigger('Plain Plugin'));

      expect(screen.queryByText('Must run after')).not.toBeInTheDocument();
    });

    it('lets the options of a plugin that is not enabled be edited', async () => {
      const user = userEvent.setup();
      const values = renderField({
        plugins: [requiredPlugin],
      });

      await user.click(trigger('Required Plugin'));
      await user.type(screen.getByRole('textbox'), 'https://example.org');

      expect(values().config).toEqual({
        Required: { options: { url: 'https://example.org' } },
      });
      expect(values().plugins).toEqual([]);
    });

    it('expands an enabled plugin with a missing required option and marks it', async () => {
      const user = userEvent.setup();
      renderField({
        plugins: [requiredPlugin],
        defaultValues: { plugins: ['Required'] },
      });

      expect(trigger('Required Plugin')).toHaveAttribute(
        'aria-expanded',
        'true'
      );
      expect(screen.getByText('1 required')).toHaveAttribute(
        'title',
        'Required options that have no value.'
      );

      await user.type(screen.getByRole('textbox'), 'https://example.org');

      expect(trigger('Required Plugin')).not.toHaveTextContent(/\d+ required/);
    });

    it('does not mark a missing required option of a plugin that is not enabled', () => {
      renderField({ plugins: [requiredPlugin] });

      expect(trigger('Required Plugin')).not.toHaveTextContent(/\d+ required/);
    });

    it('expands an enabled plugin whose options differ from the defaults', () => {
      renderField({
        plugins: [flagPlugin, fixedPlugin],
        defaultValues: {
          plugins: ['Flag', 'Fixed'],
          config: {
            Flag: { options: { flag: true } },
            Fixed: { options: { token: 'secret-token' } },
          },
        },
      });

      expect(trigger('Flag Plugin')).toHaveAttribute('aria-expanded', 'true');
      expect(trigger('Fixed Plugin')).toHaveAttribute('aria-expanded', 'false');
    });

    it('marks the number of options that differ from the defaults', async () => {
      const user = userEvent.setup();
      renderField({
        plugins: [flagPlugin],
        defaultValues: {
          plugins: ['Flag'],
          config: { Flag: { options: { flag: true } } },
        },
      });

      expect(screen.getByText('1 modified')).toHaveAttribute(
        'title',
        'Options whose values differ from their defaults.'
      );

      await user.click(screen.getByRole('checkbox', { name: /^flag/ }));

      expect(trigger('Flag Plugin')).not.toHaveTextContent('modified');
    });

    it('does not mark modified options of a plugin that is not enabled', () => {
      renderField({
        plugins: [flagPlugin],
        defaultValues: { config: { Flag: { options: { flag: true } } } },
      });

      expect(trigger('Flag Plugin')).not.toHaveTextContent('modified');
    });

    it('counts only the options that can be changed', () => {
      renderField({
        plugins: [flagPlugin, fixedPlugin],
      });

      expect(trigger('Flag Plugin')).toHaveTextContent('1 option');
      expect(trigger('Fixed Plugin')).not.toHaveTextContent('option');
    });

    it('counts the must-run-after list as an option, whether the plugin is enabled or not', async () => {
      const user = userEvent.setup();
      renderField({
        plugins: [flagPlugin, plainPlugin],
        withMustRunAfter: true,
      });

      expect(trigger('Flag Plugin')).toHaveTextContent('2 options');
      expect(trigger('Plain Plugin')).toHaveTextContent('1 option');

      await user.click(enableCheckbox('Flag Plugin'));

      expect(trigger('Flag Plugin')).toHaveTextContent('2 options');
    });

    it('counts the scanner scope as an option', () => {
      renderField({
        plugins: [flagPlugin],
        withScannerScope: true,
      });

      expect(trigger('Flag Plugin')).toHaveTextContent('2 options');
    });

    it('expands and collapses all plugins with options', async () => {
      const user = userEvent.setup();
      renderField({
        plugins: [flagPlugin, fixedPlugin, plainPlugin],
      });

      expect(expandAllButton()).toHaveTextContent('Expand all');

      await user.click(expandAllButton());

      expect(trigger('Flag Plugin')).toHaveAttribute('aria-expanded', 'true');
      expect(trigger('Fixed Plugin')).toHaveAttribute('aria-expanded', 'true');
      expect(expandAllButton()).toHaveTextContent('Collapse all');

      await user.click(expandAllButton());

      expect(trigger('Flag Plugin')).toHaveAttribute('aria-expanded', 'false');
      expect(trigger('Fixed Plugin')).toHaveAttribute('aria-expanded', 'false');
    });

    it('expands a collapsed plugin with errors on submit', async () => {
      const user = userEvent.setup();
      renderField({
        plugins: [requiredPlugin],
        defaultValues: { plugins: ['Required'] },
        // The form types the configuration loosely, so its errors need a cast.
        resolver: async () => ({
          values: {},
          errors: {
            config: {
              Required: {
                options: {
                  url: { type: 'required', message: 'The URL is missing.' },
                },
              },
            },
          } as unknown as FieldErrors<FieldFormValues>,
        }),
      });

      // The plugin starts expanded because its required option is missing.
      await user.click(trigger('Required Plugin'));
      expect(screen.queryByText('The URL to use.')).not.toBeInTheDocument();

      await user.click(screen.getByRole('button', { name: 'Submit' }));

      expect(trigger('Required Plugin')).toHaveAttribute(
        'aria-expanded',
        'true'
      );
      expect(screen.getByText('The URL to use.')).toBeInTheDocument();
    });

    it('keeps an option value after collapsing and expanding again', async () => {
      const user = userEvent.setup();
      renderField({ plugins: [requiredPlugin] });

      await user.click(trigger('Required Plugin'));
      await user.type(screen.getByRole('textbox'), 'https://example.org');
      await user.click(trigger('Required Plugin'));
      await user.click(trigger('Required Plugin'));

      expect(screen.getByRole('textbox')).toHaveValue('https://example.org');
    });

    it('keeps the drag handles and the stored order with reordering', async () => {
      const user = userEvent.setup();
      const values = renderField({
        plugins: [flagPlugin, requiredPlugin],
        enableReordering: true,
      });

      expect(
        screen.getByRole('button', { name: 'Reorder Flag' })
      ).toBeInTheDocument();

      await user.click(enableCheckbox('Required Plugin'));
      await user.click(enableCheckbox('Flag Plugin'));

      expect(values().plugins).toEqual(['Flag', 'Required']);
    });
  });
});
