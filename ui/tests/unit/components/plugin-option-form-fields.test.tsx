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

import { renderToStaticMarkup } from 'react-dom/server';
import {
  FormProvider,
  useForm,
  type DefaultValues,
  type FieldValues,
} from 'react-hook-form';
import { describe, expect, it } from 'vitest';

import type { PluginOption, PluginOptionType } from '@/api';
import { PluginOptionFormFields } from '@/routes/admin/plugins/$pluginType/$pluginId/-components/plugin-option-form-fields';

function createPluginOption(
  type: PluginOptionType,
  overrides: Partial<PluginOption> = {}
): PluginOption {
  return {
    name: `${type.toLowerCase()}Option`,
    type,
    description: `${type} option description`,
    enumEntries:
      type === 'ENUM' || type === 'ENUM_LIST' ? ['Alpha', 'Beta'] : undefined,
    isNullable: false,
    isRequired: true,
    ...overrides,
  };
}

function renderPluginOptions(
  options: PluginOption[],
  defaultValues: DefaultValues<FieldValues>
) {
  function FormHarness() {
    const form = useForm<FieldValues>({ defaultValues });

    return (
      <FormProvider {...form}>
        <PluginOptionFormFields options={options} form={form} />
      </FormProvider>
    );
  }

  return renderToStaticMarkup(<FormHarness />);
}

type OptionTestCase = {
  type: PluginOptionType;
  value: unknown;
  overrides?: Partial<PluginOption>;
  expectedMarkup: string;
};

const optionTestCases: OptionTestCase[] = [
  {
    type: 'BOOLEAN',
    value: true,
    expectedMarkup: 'role="switch"',
  },
  {
    type: 'ENUM',
    value: 'Alpha',
    expectedMarkup: 'role="combobox"',
  },
  {
    type: 'ENUM_LIST',
    value: ['Alpha'],
    expectedMarkup: 'cmdk-input=""',
  },
  {
    type: 'INTEGER',
    value: 7,
    expectedMarkup: 'type="number"',
  },
  {
    type: 'LONG',
    value: 9,
    expectedMarkup: 'type="number"',
  },
  {
    type: 'SECRET',
    value: 'secret-name',
    overrides: { isRequired: false },
    expectedMarkup: 'placeholder="(optional)"',
  },
  {
    type: 'STRING',
    value: 'text value',
    expectedMarkup: 'type="text"',
  },
  {
    type: 'STRING_LIST',
    value: 'first,second',
    overrides: { isRequired: false },
    expectedMarkup: 'placeholder="(optional)"',
  },
];

describe('PluginOptionFormFields', () => {
  it.each(optionTestCases)(
    'renders the control for a $type option',
    ({ type, value, overrides, expectedMarkup }) => {
      const option = createPluginOption(type, overrides);
      const markup = renderPluginOptions([option], {
        [option.name]: value,
        [`${option.name}_isFinal`]: false,
        [`${option.name}_isNotSet`]: false,
      });

      expect(markup).toContain(option.name);
      expect(markup).toContain(type);
      expect(markup).toContain(option.description);
      expect(markup).toContain(expectedMarkup);
      expect(markup).toContain('Final');
      expect(markup).toContain('Undefined');

      if (type === 'SECRET') {
        expect(markup).toContain(
          'This must be the name of a secret available in the config secret provider'
        );
      }
    }
  );

  it('renders values and final state from an existing template', () => {
    const option = createPluginOption('STRING', { name: 'existingOption' });
    const markup = renderPluginOptions([option], {
      existingOption: 'configured value',
      existingOption_isFinal: true,
      existingOption_isNotSet: false,
    });

    expect(markup).toContain('value="configured value"');
    expect(markup).toContain('data-state="checked"');
    expect(markup).toContain('Final');
    expect(markup).toContain('Undefined');
  });
});
