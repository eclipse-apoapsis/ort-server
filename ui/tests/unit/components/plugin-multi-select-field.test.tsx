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

import { PluginMultiSelectField } from '@/components/form/plugin-multi-select-field.tsx';
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
});
