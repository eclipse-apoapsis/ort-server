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
import { ChangeEvent } from 'react';
import { FieldValues, UseFormReturn } from 'react-hook-form';

import { PluginOption } from '@/api';
import { OptionalInput } from '@/components/form/optional-input';
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
import { Input } from '@/components/ui/input';
import MultipleSelector, {
  Option as MultipleSelectorOption,
} from '@/components/ui/multiple-selector';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Switch } from '@/components/ui/switch';
import { parseStoredPluginOptionValue } from '../-helpers.ts';

type Props = {
  options: PluginOption[];
  form: UseFormReturn<FieldValues>;
};

export function PluginOptionFormFields({ options, form }: Props) {
  function handleValueChange(optionName: string, value: string) {
    if (value !== '') {
      form.setValue(`${optionName}_isNotSet`, false);
    } else {
      form.setValue(`${optionName}_isNotSet`, true);
    }
  }

  return options.map((option) => {
    const isNotSet = form.watch(`${option.name}_isNotSet`);

    return (
      <FormItem key={option.name}>
        <FormLabel>
          {option.name}
          <Badge className='ml-2 bg-blue-200 text-black'>{option.type}</Badge>
        </FormLabel>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <FormField
            control={form.control}
            name={option.name}
            render={({ field }) => (
              <FormControl>
                {option.type === 'BOOLEAN' ? (
                  <Switch
                    checked={field.value as boolean}
                    onCheckedChange={(checked) => {
                      field.onChange(checked);
                      form.setValue(`${option.name}_isNotSet`, false);
                    }}
                  />
                ) : option.type === 'ENUM' ? (
                  <Select
                    value={typeof field.value === 'string' ? field.value : ''}
                    onValueChange={(value) => {
                      field.onChange(value);
                      handleValueChange(option.name, value);
                    }}
                  >
                    <SelectTrigger className='w-[280px]'>
                      <SelectValue placeholder='Select a value' />
                    </SelectTrigger>
                    <SelectContent>
                      {(option.enumEntries ?? []).map((entry) => (
                        <SelectItem key={entry} value={entry}>
                          {entry}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                ) : option.type === 'ENUM_LIST' ? (
                  <MultipleSelector
                    className='min-w-[280px]'
                    placeholder='Select values'
                    hidePlaceholderWhenSelected
                    value={(Array.isArray(field.value)
                      ? (field.value as string[])
                      : []
                    ).map<MultipleSelectorOption>((entry) => ({
                      value: entry,
                      label: entry,
                    }))}
                    options={(
                      option.enumEntries ?? []
                    ).map<MultipleSelectorOption>((entry) => ({
                      value: entry,
                      label: entry,
                    }))}
                    onChange={(selected) => {
                      const values = selected.map((s) => s.value);
                      field.onChange(values);
                      handleValueChange(option.name, values.join(','));
                    }}
                  />
                ) : option.isRequired ? (
                  <Input
                    {...field}
                    type={
                      option.type === 'INTEGER' || option.type === 'LONG'
                        ? 'number'
                        : 'text'
                    }
                    value={
                      typeof field.value === 'string' ||
                      typeof field.value === 'number'
                        ? field.value
                        : ''
                    }
                    onChange={(e) => {
                      field.onChange(e);
                      handleValueChange(option.name, e.target.value);
                    }}
                  />
                ) : (
                  <OptionalInput
                    {...field}
                    type={
                      option.type === 'INTEGER' || option.type === 'LONG'
                        ? 'number'
                        : 'text'
                    }
                    onChange={(e: ChangeEvent<HTMLInputElement>) => {
                      field.onChange(e);
                      handleValueChange(option.name, e.target.value);
                    }}
                  />
                )}
              </FormControl>
            )}
          />
          <FormField
            control={form.control}
            name={`${option.name}_isFinal`}
            render={({ field }) => (
              <label
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 4,
                }}
              >
                <Checkbox
                  checked={field.value as CheckedState}
                  onCheckedChange={field.onChange}
                  disabled={Boolean(isNotSet)}
                />
                <p className='text-sm'>Final</p>
              </label>
            )}
          />
          <FormField
            control={form.control}
            name={`${option.name}_isNotSet`}
            render={({ field }) => (
              <label
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 4,
                }}
              >
                <Checkbox
                  checked={field.value as CheckedState}
                  onCheckedChange={(checked) => {
                    field.onChange(checked);
                    if (checked) {
                      form.setValue(
                        option.name,
                        option.type === 'BOOLEAN'
                          ? false
                          : option.type === 'ENUM_LIST'
                            ? parseStoredPluginOptionValue(
                                option.defaultValue,
                                option.type
                              )
                            : (option.defaultValue ?? '')
                      );
                    }
                  }}
                />
                <p className='text-sm'>Undefined</p>
              </label>
            )}
          />
        </div>
        <FormDescription>
          {option.description}
          {option.type === 'SECRET' && (
            <>
              <br />
              <span className='text-red-500'>
                Enter the name of the secret, not the value! A secret with this
                name must be configured in the context of the ORT run.
              </span>
            </>
          )}
        </FormDescription>
        <FormMessage />
      </FormItem>
    );
  });
}
