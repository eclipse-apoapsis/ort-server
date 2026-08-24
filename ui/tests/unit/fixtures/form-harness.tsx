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

import type { ReactNode } from 'react';
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
