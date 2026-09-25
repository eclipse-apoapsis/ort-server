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

import { Secret } from '@/api';
import { FormControl, FormMessage } from '@/components/ui/form';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select.tsx';
import type { OptionInputProps } from '../types';
import {
  ADMIN_SECRET_VALUE,
  getSecretSelectDisplayValue,
  mapSecretSelectValue,
  UNDEFINED_SECRET_VALUE,
} from '../utils';

type SecretOptionInputProps = OptionInputProps & {
  secrets: readonly Secret[];
};

/**
 * Select a secret for a secret option. Offers the administrator's secret if the option has
 * a default, and warns if the selected secret does not exist. Shows a message instead if
 * there are neither secrets nor a default.
 */
export const SecretOptionInput = ({
  field,
  option,
  secrets,
}: SecretOptionInputProps) => (
  <FormControl>
    {secrets.length === 0 && option.defaultValue == null ? (
      <FormMessage className='font-semibold text-red-600'>
        No secrets available. Create a new secret to be able to use this option.
      </FormMessage>
    ) : (
      <Select
        onValueChange={(value) => {
          field.onChange(mapSecretSelectValue(value));
        }}
        defaultValue={undefined}
        value={getSecretSelectDisplayValue(field.value, option.isRequired)}
        disabled={option.isFixed}
      >
        <SelectTrigger>
          <SelectValue placeholder='Select a secret' />
        </SelectTrigger>
        <SelectContent>
          {option.defaultValue != null && (
            <SelectItem value={ADMIN_SECRET_VALUE}>
              Use admin-provided secret
            </SelectItem>
          )}
          {!option.isRequired && option.defaultValue == null && (
            <SelectItem value={UNDEFINED_SECRET_VALUE}>Not defined</SelectItem>
          )}
          {secrets.map((secret) => (
            <SelectItem key={secret.name} value={secret.name}>
              {secret.name}
            </SelectItem>
          ))}
        </SelectContent>
        {field.value &&
          field.value !== ADMIN_SECRET_VALUE &&
          !secrets.some((secret) => secret.name === field.value) && (
            <FormMessage className='font-semibold text-red-600'>
              The selected secret '{field.value}' does not exist. The value
              could come from a previous run or could be a default value set by
              an administrator. Select a valid secret or create a new secret
              with this name.
            </FormMessage>
          )}
      </Select>
    )}
  </FormControl>
);
