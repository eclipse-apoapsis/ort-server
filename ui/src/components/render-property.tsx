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
  type?: 'string' | 'textblock' | 'array' | 'url' | 'keyvalue';
  showIfEmpty?: boolean;
};

export const RenderProperty = <T,>({
  label,
  value,
  type = 'string',
  showIfEmpty = true,
}: RenderPropertyProps<T>) => {
  if (value || showIfEmpty) {
    switch (type) {
      case 'keyvalue': {
        const isEmpty =
          value && typeof value === 'object' && Object.keys(value).length === 0;
        return !showIfEmpty && isEmpty ? null : (
          <div className={`flex ${!isEmpty && 'flex-col'}`}>
            <div className='font-semibold'>
              {label}
              {isEmpty && ':'}
            </div>
            <FormattedValue value={value} type={type} />
          </div>
        );
      }
      case 'array': {
        const isEmpty = Array.isArray(value) && value.length === 0;
        return (
          <div className={`flex ${!isEmpty && 'flex-col'}`}>
            <div className='font-semibold'>
              {label}
              {isEmpty && ':'}
            </div>
            <FormattedValue value={value} type={type} />
          </div>
        );
      }
      case 'textblock': {
        const isEmpty =
          !value || (typeof value === 'string' && value.trim() === '');
        return (
          <div className={`flex ${!isEmpty && 'flex-col'}`}>
            <div className='font-semibold'>
              {label}
              {isEmpty && ':'}
            </div>
            <div className='ml-2 wrap-break-word'>
              <FormattedValue value={value} type={'string'} />
            </div>
          </div>
        );
      }
      default:
        return (
          <div className='flex gap-2'>
            <div className='font-semibold'>{label}:</div>
            <FormattedValue value={value} type={type} />
          </div>
        );
    }
  } else return null;
};
