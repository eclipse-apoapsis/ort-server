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
import { useForm } from 'react-hook-form';
import { beforeAll, describe, expect, it } from 'vitest';
import { z } from 'zod';

import { RoleSelect } from '@/components/role-select';
import { Form, FormField, FormItem } from '@/components/ui/form';
import { roleSchema } from '@/schemas';

type Role = z.infer<typeof roleSchema>;

const RoleSelectHarness = ({ role }: { role: Role }) => {
  const form = useForm<{ role: Role }>({ defaultValues: { role } });

  return (
    <Form {...form}>
      <FormField
        control={form.control}
        name='role'
        render={({ field }) => (
          <FormItem>
            <RoleSelect value={field.value} onChange={field.onChange} />
          </FormItem>
        )}
      />
    </Form>
  );
};

describe('RoleSelect', () => {
  beforeAll(() => {
    // Radix Select relies on DOM APIs that jsdom does not implement.
    Element.prototype.hasPointerCapture = () => false;
    Element.prototype.releasePointerCapture = () => {};
    Element.prototype.scrollIntoView = () => {};
  });

  it('shows the selected role in the trigger', () => {
    render(<RoleSelectHarness role='READER' />);

    expect(screen.getByRole('combobox')).toHaveTextContent('READER');
  });

  it('updates the field when another role is selected', async () => {
    const user = userEvent.setup();
    render(<RoleSelectHarness role='READER' />);

    await user.click(screen.getByRole('combobox'));
    await user.click(screen.getByRole('option', { name: 'ADMIN' }));

    expect(screen.getByRole('combobox')).toHaveTextContent('ADMIN');
  });
});
