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

import type { Control, FieldValues, Path } from 'react-hook-form';

import { FormControl, FormField, FormItem } from '@/components/ui/form';
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group.tsx';
import type { ScannerScope } from './types';

type ScannerScopeToggleProps<TFieldValues extends FieldValues> = {
  control: Control<TFieldValues>;
  /** The path of the plugin's scope value. */
  name: Path<TFieldValues>;
};

/** Choose whether a scanner runs on packages, projects, or both. */
export const ScannerScopeToggle = <TFieldValues extends FieldValues>({
  control,
  name,
}: ScannerScopeToggleProps<TFieldValues>) => (
  <FormField
    control={control}
    name={name}
    render={({ field }) => (
      <FormItem className='mb-2 flex flex-col space-y-1'>
        <FormControl>
          <ToggleGroup
            type='single'
            variant='outline'
            value={(field.value as ScannerScope | undefined) ?? 'both'}
            onValueChange={(value) => {
              if (value) field.onChange(value as ScannerScope);
            }}
            className='gap-0 self-start'
          >
            <ToggleGroupItem
              value='both'
              className='rounded-r-none text-xs data-[state=on]:bg-blue-500 data-[state=on]:text-white'
            >
              Both
            </ToggleGroupItem>
            <ToggleGroupItem
              value='packages'
              className='-ml-px rounded-none text-xs data-[state=on]:bg-blue-500 data-[state=on]:text-white'
            >
              Packages only
            </ToggleGroupItem>
            <ToggleGroupItem
              value='projects'
              className='-ml-px rounded-l-none text-xs data-[state=on]:bg-blue-500 data-[state=on]:text-white'
            >
              Projects only
            </ToggleGroupItem>
          </ToggleGroup>
        </FormControl>
      </FormItem>
    )}
  />
);
