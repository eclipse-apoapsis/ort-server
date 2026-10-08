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

import { render } from '@testing-library/react';
import { memo, type ReactNode } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import {
  FormProvider,
  useForm,
  type DefaultValues,
  type UseFormReturn,
} from 'react-hook-form';

import { Accordion } from '@/components/ui/accordion';
import type { CreateRunFormValues } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components';

type FormHarnessOptions = {
  defaultValues: DefaultValues<CreateRunFormValues>;
  openAccordion?: string;
};

export function renderWithForm(
  children: (form: UseFormReturn<CreateRunFormValues>) => ReactNode,
  { defaultValues, openAccordion }: FormHarnessOptions
) {
  function FormHarness() {
    const form = useForm<CreateRunFormValues>({ defaultValues });
    const content = children(form);

    return (
      <FormProvider {...form}>
        {openAccordion ? (
          <Accordion type='multiple' defaultValue={[openAccordion]}>
            {content}
          </Accordion>
        ) : (
          content
        )}
      </FormProvider>
    );
  }

  return renderToStaticMarkup(<FormHarness />);
}

/**
 * Create a harness that renders the content inside a parent that receives the
 * form and never renders again, as a parent memoized by React Compiler does. A
 * field group under it shows a changed value only if it subscribes to that
 * value itself.
 */
export function createStableFormHarness(
  children: (form: UseFormReturn<CreateRunFormValues>) => ReactNode,
  { defaultValues }: Pick<FormHarnessOptions, 'defaultValues'>
) {
  const StableParent = memo(function StableParent({
    form,
  }: {
    form: UseFormReturn<CreateRunFormValues>;
  }) {
    return children(form);
  });

  let form: UseFormReturn<CreateRunFormValues> | undefined;

  function StableFormHarness() {
    form = useForm<CreateRunFormValues>({ defaultValues });

    return (
      <FormProvider {...form}>
        <StableParent form={form} />
      </FormProvider>
    );
  }

  const getForm = () => {
    if (!form) throw new Error('The form harness has not rendered yet.');
    return form;
  };

  return { StableFormHarness, getForm };
}

/** Render the content with {@link createStableFormHarness}. */
export function renderWithStableForm(
  children: (form: UseFormReturn<CreateRunFormValues>) => ReactNode,
  options: Pick<FormHarnessOptions, 'defaultValues'>
) {
  const { StableFormHarness, getForm } = createStableFormHarness(
    children,
    options
  );
  const result = render(<StableFormHarness />);

  return { ...result, form: getForm() };
}
