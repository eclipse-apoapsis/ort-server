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
import { FormProvider, useForm } from 'react-hook-form';
import { describe, expect, it } from 'vitest';

import { useFormField } from '@/components/ui/form-context';

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

describe('useFormField', () => {
  it('throws when used outside a FormField', () => {
    expect(() => renderToStaticMarkup(<FormWithoutField />)).toThrow(
      'useFormField should be used within <FormField>'
    );
  });
});
