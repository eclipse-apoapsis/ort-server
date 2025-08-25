/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import z from 'zod';

import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';

export const DEFAULT_RUNS = 100;

/**
 * Internal form shape:
 * - nRuns is a string and OPTIONAL so the resolver type is `string | undefined`.
 * - It's presence and validity is enforced ONLY when fetchMode === "CUSTOM_RUNS".
 * - Internally, the type of the nRuns input field needs to be a string to prevent
 *   React from falling back to showing last valid value when the input is cleared to empty.
 */
const formSchema = z
  .object({
    fetchMode: z.enum(['VISIBLE_RUNS', 'CUSTOM_RUNS']),
    nRuns: z.string().optional(),
  })
  .superRefine((data, ctx) => {
    if (data.fetchMode === 'CUSTOM_RUNS') {
      const raw = (data.nRuns ?? '').trim();
      if (raw === '') {
        ctx.addIssue({
          path: ['nRuns'],
          code: 'custom',
          message: 'Please enter the number of runs.',
        });
        return;
      }
      // Only digits and >= 1.
      if (!/^\d+$/.test(raw) || Number(raw) < 1) {
        ctx.addIssue({
          path: ['nRuns'],
          code: 'custom',
          message: 'Must be a positive number.',
        });
      }
    }
  });

type FormValues = z.infer<typeof formSchema>;

// External contract for the parent: number on submit.
export type RunsFilterValues = {
  fetchMode: 'VISIBLE_RUNS' | 'CUSTOM_RUNS';
  nRuns?: number;
};

type RunsFilterFormProps = {
  initialValues?: RunsFilterValues;
  onApply: (values: RunsFilterValues) => void;
};

export function RunsFilterForm({
  initialValues,
  onApply,
}: RunsFilterFormProps) {
  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      fetchMode: initialValues?.fetchMode ?? 'VISIBLE_RUNS',
      // Keep as string for editing; provide a starting value.
      nRuns:
        initialValues?.nRuns !== undefined
          ? String(initialValues.nRuns)
          : String(DEFAULT_RUNS),
    },
    mode: 'onSubmit',
  });

  const onSubmit = (data: FormValues) => {
    const raw = (data.nRuns ?? '').trim();
    onApply({
      fetchMode: data.fetchMode,
      nRuns:
        data.fetchMode === 'CUSTOM_RUNS'
          ? raw === ''
            ? undefined
            : Number(raw)
          : undefined,
    });
  };

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)}>
        <FormField
          control={form.control}
          name='fetchMode'
          render={({ field }) => (
            <FormItem>
              <FormControl>
                <RadioGroup
                  className='flex'
                  value={field.value}
                  onValueChange={(value) => {
                    field.onChange(value);
                    // Apply immediately on radio change.
                    form.handleSubmit(onSubmit)();
                  }}
                >
                  <FormItem className='flex items-center space-x-2'>
                    <FormControl>
                      <RadioGroupItem
                        className='ml-2'
                        id='visible-runs'
                        value='VISIBLE_RUNS'
                      />
                    </FormControl>
                    <FormLabel htmlFor='visible-runs' className='-ml-2'>
                      visible runs
                    </FormLabel>
                  </FormItem>

                  <FormItem className='flex items-center space-x-2'>
                    <FormControl>
                      <RadioGroupItem
                        className='ml-2'
                        id='custom-runs'
                        value='CUSTOM_RUNS'
                      />
                    </FormControl>
                    <FormLabel htmlFor='custom-runs' className='-ml-2'>
                      last
                      <FormField
                        control={form.control}
                        name='nRuns'
                        render={({ field }) => (
                          <FormItem>
                            <FormControl>
                              <Input
                                // Allow empty while editing.
                                value={field.value ?? ''}
                                onFocus={() => {
                                  if (
                                    form.getValues('fetchMode') !==
                                    'CUSTOM_RUNS'
                                  ) {
                                    form.setValue('fetchMode', 'CUSTOM_RUNS', {
                                      shouldDirty: true,
                                      shouldTouch: true,
                                    });
                                  }
                                  // Submit on focus to apply the current value.
                                  form.handleSubmit(onSubmit)();
                                }}
                                onChange={(e) => {
                                  // Do not submit on change; just store the raw string.
                                  if (
                                    form.getValues('fetchMode') !==
                                    'CUSTOM_RUNS'
                                  ) {
                                    form.setValue('fetchMode', 'CUSTOM_RUNS', {
                                      shouldDirty: true,
                                      shouldTouch: true,
                                    });
                                  }
                                  field.onChange(e.target.value);
                                }}
                                className='w-14 [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none'
                              />
                            </FormControl>
                            {/* Field-level validation message for nRuns */}
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                      runs
                    </FormLabel>
                  </FormItem>
                </RadioGroup>
              </FormControl>
            </FormItem>
          )}
        />
        {/* Ensure Enter always submits even without a visible button */}
        <input type='submit' hidden />
      </form>
    </Form>
  );
}
