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

import type {
  Control,
  ControllerRenderProps,
  FieldValues,
  Path,
} from 'react-hook-form';

import type { PreconfiguredPluginOption, Secret } from '@/api';
import { Badge } from '@/components/ui/badge.tsx';
import {
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
} from '@/components/ui/form';
import { BooleanOptionInput } from './option-inputs/boolean-option-input';
import { EnumListOptionInput } from './option-inputs/enum-list-option-input';
import { EnumOptionInput } from './option-inputs/enum-option-input';
import { SecretOptionInput } from './option-inputs/secret-option-input';
import { TextOptionInput } from './option-inputs/text-option-input';

type PluginOptionFieldProps<TFieldValues extends FieldValues> = {
  control: Control<TFieldValues>;
  /** The path of the option's value, see `optionFieldPath`. */
  name: Path<TFieldValues>;
  option: PreconfiguredPluginOption;
  secrets: readonly Secret[];
};

/** Choose the input for an option based on its type. */
const renderOptionInput = (
  field: ControllerRenderProps,
  option: PreconfiguredPluginOption,
  secrets: readonly Secret[]
) => {
  if (option.type === 'BOOLEAN') {
    return <BooleanOptionInput field={field} option={option} />;
  }

  if (option.type === 'SECRET') {
    return (
      <SecretOptionInput field={field} option={option} secrets={secrets} />
    );
  }

  if (
    option.type === 'ENUM' &&
    option.enumEntries &&
    option.enumEntries.length > 0
  ) {
    return (
      <EnumOptionInput
        field={field}
        option={option}
        enumEntries={option.enumEntries}
      />
    );
  }

  if (option.type === 'ENUM_LIST') {
    return <EnumListOptionInput field={field} option={option} />;
  }

  return <TextOptionInput field={field} option={option} />;
};

/**
 * A form field for one plugin option: its name and type, an input matching the type, its
 * description, and a notice if an administrator has fixed the value.
 */
export const PluginOptionField = <TFieldValues extends FieldValues>({
  control,
  name,
  option,
  secrets,
}: PluginOptionFieldProps<TFieldValues>) => (
  <FormField
    control={control}
    name={name}
    render={({ field }) => (
      <FormItem className='ml-4 flex flex-col pb-4'>
        <FormLabel>
          {option.name}
          <Badge variant='small' className='bg-blue-200 text-black'>
            {option.type}
          </Badge>
        </FormLabel>
        {/* The inputs do not depend on the form's value types, so drop them here. */}
        {renderOptionInput(field as ControllerRenderProps, option, secrets)}
        <FormDescription>{option.description}</FormDescription>
        {option.isFixed && (
          <FormDescription className='font-semibold text-yellow-700'>
            This option is set by an administrator and cannot be changed.
          </FormDescription>
        )}
      </FormItem>
    )}
  />
);
