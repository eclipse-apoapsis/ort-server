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

import { FormattedValue } from '@/components/formatted-value';

type RenderPropertyProps<T> = {
  label: string;
  value: T | T[] | Record<string, unknown> | null | undefined;
  type?: 'string' | 'array' | 'url' | 'keyvalue';
  showIfEmpty?: boolean;
};

export const RenderProperty = <T,>({
  label,
  value,
  type = 'string',
  showIfEmpty = true,
}: RenderPropertyProps<T>) => {
  return value || showIfEmpty ? (
    type === 'keyvalue' ? (
      <div>
        <div className='font-semibold'>{label}</div>
        <FormattedValue value={value} type={type} />
      </div>
    ) : (
      <div className='flex gap-2'>
        <div className='font-semibold'>{label}:</div>
        <FormattedValue value={value} type={type} />
      </div>
    )
  ) : null;
};
