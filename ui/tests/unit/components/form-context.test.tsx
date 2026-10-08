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
import { renderToStaticMarkup } from 'react-dom/server';
import { FormProvider, useForm } from 'react-hook-form';
import { describe, expect, it } from 'vitest';

import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { useFormField } from '@/components/ui/form-context';
import { Input } from '@/components/ui/input';
import { isCompiledByReactCompiler } from '../fixtures/react-compiler';

const FieldConsumer = () => <>{useFormField().id}</>;

// The hook is only reachable from inside a form, so the surrounding provider is
// what a real caller would have; only the <FormField> around it is missing.
const FormWithoutField = () => {
  const form = useForm();

  return (
    <FormProvider {...form}>
      <FieldConsumer />
    </FormProvider>
  );
};

const RequiredNameForm = () => {
  const form = useForm<{ name: string }>({ defaultValues: { name: '' } });

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(() => {})}>
        <FormField
          control={form.control}
          name='name'
          rules={{ required: 'Name is required' }}
          render={({ field }) => (
            <FormItem>
              <FormLabel>Name</FormLabel>
              <FormControl>
                <Input {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <button type='submit'>Submit</button>
      </form>
    </Form>
  );
};

describe('useFormField', () => {
  it('is compiled with React Compiler', () => {
    expect(isCompiledByReactCompiler(useFormField)).toBe(true);
  });

  // The hook reads the field state through `useFormContext()` and subscribes
  // with `useFormState`, which React Hook Form reports as the safe pattern
  // under React Compiler.
  it('shows the error of a field until it is valid', async () => {
    const user = userEvent.setup();
    render(<RequiredNameForm />);

    await user.click(screen.getByRole('button', { name: 'Submit' }));

    expect(await screen.findByText('Name is required')).toBeInTheDocument();
    expect(screen.getByLabelText('Name')).toHaveAttribute(
      'aria-invalid',
      'true'
    );

    await user.type(screen.getByLabelText('Name'), 'core');

    expect(screen.queryByText('Name is required')).not.toBeInTheDocument();
  });

  it('throws when used outside a FormField', () => {
    expect(() => renderToStaticMarkup(<FormWithoutField />)).toThrow(
      'useFormField should be used within <FormField>'
    );
  });
});
