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
import { FormProvider, useForm } from 'react-hook-form';
import { describe, expect, it } from 'vitest';

import { MultiSelectField } from '@/components/form/multi-select-field';

const options = [
  { id: 'scan', label: 'Scan' },
  { id: 'report', label: 'Report' },
];

const MultiSelectHarness = () => {
  const form = useForm<{ phases: string[] }>({
    defaultValues: { phases: [] },
  });

  return (
    <FormProvider {...form}>
      <MultiSelectField
        form={form}
        name='phases'
        label='Phases'
        options={options}
      />
    </FormProvider>
  );
};

// The select-all checkbox reads the selection with `getValues()` inside the
// field's render function, which React Hook Form reports as fragile under
// React Compiler.
describe('MultiSelectField', () => {
  it('shows whether none, some or all options are selected', async () => {
    const user = userEvent.setup();
    render(<MultiSelectHarness />);
    const selectAll = screen.getByRole('checkbox', {
      name: 'Enable/disable all',
    });

    expect(selectAll).toHaveAttribute('aria-checked', 'false');

    await user.click(screen.getByRole('checkbox', { name: 'Scan' }));

    expect(selectAll).toHaveAttribute('aria-checked', 'mixed');

    await user.click(screen.getByRole('checkbox', { name: 'Report' }));

    expect(selectAll).toHaveAttribute('aria-checked', 'true');
  });

  it('selects and clears all options', async () => {
    const user = userEvent.setup();
    render(<MultiSelectHarness />);
    const selectAll = screen.getByRole('checkbox', {
      name: 'Enable/disable all',
    });

    await user.click(selectAll);

    expect(screen.getByRole('checkbox', { name: 'Scan' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Report' })).toBeChecked();

    await user.click(selectAll);

    expect(screen.getByRole('checkbox', { name: 'Scan' })).not.toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Report' })).not.toBeChecked();
  });
});
