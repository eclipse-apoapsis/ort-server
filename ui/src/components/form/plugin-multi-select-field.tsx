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

import { CheckedState } from '@radix-ui/react-checkbox';
import React from 'react';
import {
  FieldPathByValue,
  FieldPathValue,
  FieldValues,
  Path,
  UseFormReturn,
} from 'react-hook-form';

import { PreconfiguredPluginDescriptor, Secret } from '@/api/requests';
import { OptionalInput } from '@/components/form/optional-input.tsx';
import { Badge } from '@/components/ui/badge.tsx';
import { Checkbox } from '@/components/ui/checkbox';
import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input.tsx';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select.tsx';
import { Separator } from '@/components/ui/separator';
import { cn } from '@/lib/utils';

type PluginMultiSelectFieldProps<
  TFieldValues extends FieldValues,
  TName extends FieldPathByValue<TFieldValues, Array<string>>,
> = {
  form: UseFormReturn<TFieldValues, TName>;
  name: TName;
  configName: TName;
  label?: string;
  description?: React.ReactNode;
  plugins: readonly PreconfiguredPluginDescriptor[];
  secrets: readonly Secret[];
  className?: string;
};

export const PluginMultiSelectField = <
  TFieldValues extends FieldValues,
  TName extends FieldPathByValue<TFieldValues, Array<string>>,
>({
  form,
  name,
  configName,
  label,
  description,
  plugins,
  secrets,
  className,
}: PluginMultiSelectFieldProps<TFieldValues, TName>) => {
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
                plugins.every((plugin) =>
                  form.getValues(name).includes(plugin.id)
                )
                  ? true
                  : plugins.some((plugin) =>
                        form.getValues(name).includes(plugin.id)
                      )
                    ? 'indeterminate'
                    : false
              }
              onCheckedChange={(checked) => {
                const enabledItems = checked
                  ? plugins.map((plugin) => plugin.id)
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
          {plugins.map((plugin) => (
            <FormItem
              key={plugin.id}
              className='flex flex-row items-start space-y-0 space-x-3'
            >
              <FormControl>
                <Checkbox
                  checked={field.value?.includes(plugin.id)}
                  onCheckedChange={(checked) => {
                    return checked
                      ? field.onChange([...field.value, plugin.id])
                      : field.onChange(
                          field.value?.filter(
                            (value: string) => value !== plugin.id
                          )
                        );
                  }}
                />
              </FormControl>
              <div className='flex flex-col'>
                <FormLabel className='font-normal'>
                  {plugin.displayName}
                </FormLabel>
                {plugin.description != null && (
                  <FormDescription className='pb-4'>
                    {plugin.description}
                  </FormDescription>
                )}
                {field.value?.includes(plugin.id) &&
                  plugin.options.map((option) => (
                    <FormField
                      control={form.control}
                      key={option.name}
                      name={
                        `${configName}.${plugin.id}.${option.type === 'SECRET' ? 'secrets' : 'options'}.${option.name}` as Path<TFieldValues>
                      }
                      render={({ field }) => (
                        <FormItem className='flex flex-col space-y-1'>
                          <FormLabel className='mt-2'>
                            {option.name}
                            <Badge className='ml-2 bg-blue-200 text-black'>
                              {option.type}
                            </Badge>
                          </FormLabel>
                          <FormDescription>
                            {option.description}
                          </FormDescription>
                          <FormControl>
                            {option.type === 'BOOLEAN' ? (
                              <Checkbox
                                checked={field.value as CheckedState}
                                onCheckedChange={field.onChange}
                                disabled={option.isFixed}
                              />
                            ) : option.type == 'SECRET' ? (
                              secrets.length === 0 ? (
                                <FormMessage className='font-semibold text-red-600'>
                                  No secrets available. Create a new secret to
                                  be able to use this option.
                                </FormMessage>
                              ) : (
                                <Select
                                  onValueChange={field.onChange}
                                  defaultValue={undefined}
                                  value={field.value}
                                  disabled={option.isFixed}
                                >
                                  <SelectTrigger>
                                    <SelectValue placeholder='Select a secret' />
                                  </SelectTrigger>
                                  <SelectContent>
                                    {secrets.map((secret) => (
                                      <SelectItem
                                        key={secret.name}
                                        value={secret.name}
                                      >
                                        {secret.name}
                                      </SelectItem>
                                    ))}
                                  </SelectContent>
                                  {field.value &&
                                    !secrets.some(
                                      (secret) => secret.name === field.value
                                    ) && (
                                      <FormMessage className='font-semibold text-red-600'>
                                        The selected secret '{field.value}' does
                                        not exist. The value could come from a
                                        previous run or could be a default value
                                        set by an administrator. Select a valid
                                        secret or create a new secret with this
                                        name.
                                      </FormMessage>
                                    )}
                                </Select>
                              )
                            ) : option.isRequired ? (
                              <Input
                                {...field}
                                type={
                                  option.type === 'INTEGER' ||
                                  option.type === 'LONG'
                                    ? 'number'
                                    : 'text'
                                }
                                value={field.value}
                                disabled={option.isFixed}
                              />
                            ) : (
                              <OptionalInput
                                {...field}
                                type={
                                  option.type === 'INTEGER' ||
                                  option.type === 'LONG'
                                    ? 'number'
                                    : 'text'
                                }
                                value={field.value}
                                disabled={option.isFixed}
                              />
                            )}
                          </FormControl>
                          {option.isFixed && (
                            <FormDescription className='text-muted-foreground font-semibold text-yellow-700'>
                              This option is set by an administrator and cannot
                              be changed.
                            </FormDescription>
                          )}
                        </FormItem>
                      )}
                    />
                  ))}
              </div>
            </FormItem>
          ))}
          <FormMessage />
        </FormItem>
      )}
    />
  );
};
