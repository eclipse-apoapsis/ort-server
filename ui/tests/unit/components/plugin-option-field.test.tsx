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
import { beforeAll, describe, expect, it } from 'vitest';

import type { PreconfiguredPluginOption, Secret } from '@/api';
import { PluginOptionField } from '@/components/form/plugin-multi-select-field/plugin-option-field';

type FormValues = { value?: unknown };

const createOption = (
  overrides: Partial<PreconfiguredPluginOption> = {}
): PreconfiguredPluginOption => ({
  name: 'setting',
  type: 'STRING',
  description: 'The setting description.',
  isFixed: false,
  isNullable: false,
  isRequired: false,
  ...overrides,
});

/** Render one option field and return a function that reads its value. */
const renderOptionField = (
  option: PreconfiguredPluginOption,
  { value, secrets = [] }: { value?: unknown; secrets?: Secret[] } = {}
) => {
  let form: UseFormReturn<FormValues> | undefined;

  const Harness = () => {
    form = useForm<FormValues>({ defaultValues: { value } });

    return (
      <FormProvider {...form}>
        <PluginOptionField
          control={form.control}
          name='value'
          option={option}
          secrets={secrets}
        />
      </FormProvider>
    );
  };

  render(<Harness />);

  return () => form!.getValues().value;
};

describe('PluginOptionField', () => {
  beforeAll(() => {
    // Radix Select relies on DOM APIs that jsdom does not implement.
    Element.prototype.hasPointerCapture = () => false;
    Element.prototype.releasePointerCapture = () => {};
    Element.prototype.scrollIntoView = () => {};
  });

  it('renders the option name, type and description', () => {
    renderOptionField(createOption());

    expect(screen.getByText('setting')).toBeInTheDocument();
    expect(screen.getByText('STRING')).toBeInTheDocument();
    expect(screen.getByText('The setting description.')).toBeInTheDocument();
  });

  it('renders a checkbox for a BOOLEAN option', async () => {
    const user = userEvent.setup();
    const getValue = renderOptionField(createOption({ type: 'BOOLEAN' }), {
      value: false,
    });

    await user.click(screen.getByRole('checkbox', { name: /setting/ }));

    expect(getValue()).toBe(true);
  });

  describe('text inputs', () => {
    it('renders a text input without placeholder for a required option', () => {
      renderOptionField(createOption({ isRequired: true }));

      const input = screen.getByRole('textbox', { name: /setting/ });
      expect(input).toHaveAttribute('type', 'text');
      expect(input).not.toHaveAttribute('placeholder');
    });

    it('renders a text input with an optional placeholder for an optional option', () => {
      renderOptionField(createOption());

      expect(screen.getByRole('textbox', { name: /setting/ })).toHaveAttribute(
        'placeholder',
        '(optional)'
      );
    });

    it.each(['INTEGER', 'LONG'] as const)(
      'renders a number input for a %s option',
      (type) => {
        renderOptionField(createOption({ type }));

        expect(
          screen.getByRole('spinbutton', { name: /setting/ })
        ).toBeInTheDocument();
      }
    );

    it('renders a text input for an ENUM option without entries', () => {
      renderOptionField(createOption({ type: 'ENUM', enumEntries: [] }));

      expect(
        screen.getByRole('textbox', { name: /setting/ })
      ).toBeInTheDocument();
    });

    it('disables the input of a fixed option and shows the notice', () => {
      renderOptionField(createOption({ isFixed: true }));

      expect(screen.getByRole('textbox', { name: /setting/ })).toBeDisabled();
      expect(
        screen.getByText(
          'This option is set by an administrator and cannot be changed.'
        )
      ).toBeInTheDocument();
    });
  });

  describe('ENUM option', () => {
    const enumOption = (overrides: Partial<PreconfiguredPluginOption> = {}) =>
      createOption({
        type: 'ENUM',
        enumEntries: ['Alpha', 'Beta'],
        ...overrides,
      });

    it('stores the selected entry', async () => {
      const user = userEvent.setup();
      const getValue = renderOptionField(enumOption({ isRequired: true }));

      await user.click(screen.getByRole('combobox'));
      await user.click(screen.getByRole('option', { name: 'Beta' }));

      expect(getValue()).toBe('Beta');
    });

    it('offers no empty entry for a required option', async () => {
      const user = userEvent.setup();
      renderOptionField(enumOption({ isRequired: true }));

      await user.click(screen.getByRole('combobox'));

      expect(
        screen.getAllByRole('option').map((option) => option.textContent)
      ).toEqual(['Alpha', 'Beta']);
    });

    it('offers "Not defined" for an optional option without default', async () => {
      const user = userEvent.setup();
      const getValue = renderOptionField(enumOption(), { value: 'Alpha' });

      await user.click(screen.getByRole('combobox'));
      await user.click(screen.getByRole('option', { name: 'Not defined' }));

      expect(getValue()).toBeUndefined();
    });

    it('offers "Reset to default" for an optional option with default', async () => {
      const user = userEvent.setup();
      renderOptionField(enumOption({ defaultValue: 'Beta' }));

      await user.click(screen.getByRole('combobox'));

      expect(
        screen.getByRole('option', { name: 'Reset to default' })
      ).toBeInTheDocument();
    });
  });

  it('renders a multi-select with the selected values for an ENUM_LIST option', () => {
    renderOptionField(
      createOption({ type: 'ENUM_LIST', enumEntries: ['Alpha', 'Beta'] }),
      { value: 'Alpha, Beta' }
    );

    expect(screen.getByText('Alpha')).toBeInTheDocument();
    expect(screen.getByText('Beta')).toBeInTheDocument();
  });

  describe('SECRET option', () => {
    const secretOption = (overrides: Partial<PreconfiguredPluginOption> = {}) =>
      createOption({ type: 'SECRET', ...overrides });

    it('explains that no secrets exist when there are none and no default', () => {
      renderOptionField(secretOption());

      expect(
        screen.getByText(
          'No secrets available. Create a new secret to be able to use this option.'
        )
      ).toBeInTheDocument();
      expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
    });

    it('offers the admin-provided secret when the option has a default', async () => {
      const user = userEvent.setup();
      const getValue = renderOptionField(
        secretOption({ defaultValue: 'admin-secret' })
      );

      await user.click(screen.getByRole('combobox'));
      await user.click(
        screen.getByRole('option', { name: 'Use admin-provided secret' })
      );

      expect(getValue()).toBe('__admin_secret__');
    });

    it('offers the available secrets and "Not defined" for an optional option', async () => {
      const user = userEvent.setup();
      const getValue = renderOptionField(secretOption(), {
        secrets: [{ name: 'token' }],
      });

      await user.click(screen.getByRole('combobox'));

      expect(
        screen.getAllByRole('option').map((option) => option.textContent)
      ).toEqual(['Not defined', 'token']);

      await user.click(screen.getByRole('option', { name: 'token' }));

      expect(getValue()).toBe('token');
    });

    it('warns when the selected secret does not exist', () => {
      renderOptionField(secretOption(), {
        value: 'missing',
        secrets: [{ name: 'token' }],
      });

      expect(
        screen.getByText(/The selected secret 'missing' does not exist/)
      ).toBeInTheDocument();
    });
  });
});
