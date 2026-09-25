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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select.tsx';
import type { OptionInputProps } from '../types';
import {
  getEnumSelectDisplayValue,
  mapEnumSelectValue,
  UNDEFINED_ENUM_VALUE,
} from '../utils';

type EnumOptionInputProps = OptionInputProps & {
  /** The allowed values; callers use a text input instead when there are none. */
  enumEntries: readonly string[];
};

/**
 * Select one entry of an enum option. An optional option also offers "Reset to default" if
 * it has a default, or "Not defined" otherwise.
 */
export const EnumOptionInput = ({
  field,
  option,
  enumEntries,
}: EnumOptionInputProps) => (
  <FormControl>
    <Select
      onValueChange={(value) => {
        field.onChange(mapEnumSelectValue(value));
      }}
      value={getEnumSelectDisplayValue(
        typeof field.value === 'string' ? field.value : undefined,
        option.isRequired
      )}
      disabled={option.isFixed}
    >
      <SelectTrigger className='w-[280px]'>
        <SelectValue placeholder='Select a value' />
      </SelectTrigger>
      <SelectContent>
        {!option.isRequired && (
          <SelectItem value={UNDEFINED_ENUM_VALUE}>
            {option.defaultValue != null && option.defaultValue !== ''
              ? 'Reset to default'
              : 'Not defined'}
          </SelectItem>
        )}
        {enumEntries.map((entry) => (
          <SelectItem key={entry} value={entry}>
            {entry}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  </FormControl>
);
