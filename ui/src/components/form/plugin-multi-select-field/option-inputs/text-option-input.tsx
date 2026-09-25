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

import { OptionalInput } from '@/components/form/optional-input.tsx';
import { FormControl } from '@/components/ui/form';
import { Input } from '@/components/ui/input.tsx';
import type { OptionInputProps } from '../types';

/**
 * A free-text input for options without a dedicated input. Integer options use a number
 * input, and optional options show an "(optional)" placeholder.
 */
export const TextOptionInput = ({ field, option }: OptionInputProps) => {
  const type =
    option.type === 'INTEGER' || option.type === 'LONG' ? 'number' : 'text';

  return (
    <FormControl>
      {option.isRequired ? (
        <Input
          {...field}
          type={type}
          value={field.value}
          disabled={option.isFixed}
        />
      ) : (
        <OptionalInput
          {...field}
          type={type}
          value={field.value}
          disabled={option.isFixed}
        />
      )}
    </FormControl>
  );
};
