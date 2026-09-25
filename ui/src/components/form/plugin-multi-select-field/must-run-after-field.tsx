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

import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
} from '@/components/ui/form';
import MultipleSelector from '@/components/ui/multiple-selector';
import { parsePluginOptionList, toSelectorOptions } from './utils';

type MustRunAfterFieldProps<TFieldValues extends FieldValues> = {
  control: Control<TFieldValues>;
  /** The path of the plugin's must-run-after list. */
  name: Path<TFieldValues>;
  /** The ids of the plugins this plugin can be ordered after. */
  candidateIds: readonly string[];
};

/** Select the package managers that a package manager must run after. */
export const MustRunAfterField = <TFieldValues extends FieldValues>({
  control,
  name,
  candidateIds,
}: MustRunAfterFieldProps<TFieldValues>) => (
  <FormField
    control={control}
    name={name}
    render={({ field }) => (
      <FormItem className='ml-4 flex flex-col pb-4'>
        <FormLabel>Must run after</FormLabel>
        <FormControl>
          <MultipleSelector
            className='min-w-[280px]'
            placeholder='Select values'
            hidePlaceholderWhenSelected
            value={toSelectorOptions(parsePluginOptionList(field.value))}
            options={toSelectorOptions(candidateIds)}
            onChange={(selected) => {
              field.onChange(selected.map((entry) => entry.value));
            }}
          />
        </FormControl>
        <FormDescription>
          A list of package manager names that this package manager must run
          after. For example, this can be used, if another package manager
          generates files that this package manager requires to run correctly.
        </FormDescription>
      </FormItem>
    )}
  />
);
