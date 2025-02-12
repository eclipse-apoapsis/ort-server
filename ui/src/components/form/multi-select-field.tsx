/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import React from 'react';
import {
  FieldPathByValue,
  FieldPathValue,
  FieldValues,
  UseFormReturn,
} from 'react-hook-form';

import { Checkbox } from '@/components/ui/checkbox';
import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Label } from '@/components/ui/label';
import { Separator } from '@/components/ui/separator';
import { cn } from '@/lib/utils';

type MultiSelectFieldProps<
  TFieldValues extends FieldValues,
  TName extends FieldPathByValue<TFieldValues, Array<string>>,
> = {
  form: UseFormReturn<TFieldValues, TName>;
  name: TName;
  label?: string;
  description?: React.ReactNode;
  options: readonly { id: string; label: string }[];
  className?: string;
};

export const MultiSelectField = <
  TFieldValues extends FieldValues,
  TName extends FieldPathByValue<TFieldValues, Array<string>>,
>({
  form,
  name,
  label,
  description,
  options,
  className,
}: MultiSelectFieldProps<TFieldValues, TName>) => {
  return (
    <FormField
      control={form.control}
      name={name}
      render={({ field }) => (
        <FormItem
          className={cn(
            'mb-4 flex flex-col justify-between rounded-lg border p-4',
            className
          )}
        >
          <FormLabel>{label}</FormLabel>
          <FormDescription className='pb-4'>{description}</FormDescription>
          <div className='flex items-center space-x-3'>
            <Checkbox
              id='check-all-items'
              checked={
                options.every((option) =>
                  form.getValues(name).includes(option.id)
                )
                  ? true
                  : options.some((option) =>
                        form.getValues(name).includes(option.id)
                      )
                    ? 'indeterminate'
                    : false
              }
              onCheckedChange={(checked) => {
                const enabledItems = checked
                  ? options.map((option) => option.id)
                  : [];
                form.setValue(
                  name,
                  // TypeScript doesn't get this, but TName extends FieldPathByValue<TFieldValues, Array<string>>,
                  // so the field behind TName is always an Array<string>
                  // and options.map((option) => option.id) is also Array<string>,
                  // so this type cast is safe.
                  enabledItems as FieldPathValue<TFieldValues, TName>
                );
              }}
            />
            <Label htmlFor='check-all-items' className='font-bold'>
              Enable/disable all
            </Label>
          </div>
          <Separator />
          {options.map((option) => (
            <FormItem
              key={option.id}
              className='flex flex-row items-start space-y-0 space-x-3'
            >
              <FormControl>
                <Checkbox
                  checked={field.value?.includes(option.id)}
                  onCheckedChange={(checked) => {
                    return checked
                      ? field.onChange([...field.value, option.id])
                      : field.onChange(
                          field.value?.filter(
                            (value: string) => value !== option.id
                          )
                        );
                  }}
                />
              </FormControl>
              <FormLabel className='font-normal'>{option.label}</FormLabel>
            </FormItem>
          ))}
          <FormMessage />
        </FormItem>
      )}
    />
  );
};
