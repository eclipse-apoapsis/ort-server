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

import { FormControl } from '@/components/ui/form';
import MultipleSelector from '@/components/ui/multiple-selector';
import type { OptionInputProps } from '../types';
import { parsePluginOptionList, toSelectorOptions } from '../utils';

export const EnumListOptionInput = ({ field, option }: OptionInputProps) => (
  <FormControl>
    <MultipleSelector
      className='min-w-[280px]'
      placeholder='Select values'
      hidePlaceholderWhenSelected
      value={toSelectorOptions(parsePluginOptionList(field.value))}
      options={toSelectorOptions(option.enumEntries ?? [])}
      onChange={(selected) => {
        field.onChange(selected.map((entry) => entry.value));
      }}
      disabled={option.isFixed}
    />
  </FormControl>
);
