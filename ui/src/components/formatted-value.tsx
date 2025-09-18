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

import { valueOrNa } from '@/helpers/value-or-na'; // Adjust import path as needed

type FormattedValueProps<T> = {
  value: T | T[] | Record<string, unknown> | null | undefined;
  type?: 'string' | 'array' | 'url' | 'keyvalue';
};

export const FormattedValue = <T,>({
  value,
  type = 'string',
}: FormattedValueProps<T>) => {
  const arrayFormat =
    type === 'array' || type === 'keyvalue' ? 'array' : 'string';
  const result = valueOrNa(value, { arrayFormat });
  const isNa = result === 'N/A';

  // Render array
  if (type === 'array') {
    if (Array.isArray(result)) {
      return (
        <div className='flex flex-col'>
          {result.sort().map((item, idx) => (
            <div className='text-muted-foreground ml-2' key={idx}>
              {String(item)}
            </div>
          ))}
        </div>
      );
    }
    return <div className='text-muted-foreground ml-2'>{String(result)}</div>;
  }

  // Render keyvalue
  if (type === 'keyvalue') {
    if (
      result !== 'N/A' &&
      value &&
      typeof value === 'object' &&
      !Array.isArray(value)
    ) {
      const entries = Object.entries(value as Record<string, unknown>);
      if (entries.length > 0) {
        return (
          <div className='flex flex-col'>
            {entries
              .sort(([a], [b]) => a.localeCompare(b))
              .map(([key, val], idx) => (
                <div className='text-muted-foreground ml-2' key={idx}>
                  {`${key}: ${String(val)}`}
                </div>
              ))}
          </div>
        );
      }
    }
    return <div className='text-muted-foreground ml-2'>N/A</div>;
  }

  // Render as link
  if (type === 'url' && !isNa && typeof result === 'string') {
    return (
      <a
        href={result}
        target='_blank'
        rel='noopener noreferrer'
        className='text-blue-400 hover:underline'
      >
        {result}
      </a>
    );
  }

  // Default render
  return <div className={'text-muted-foreground'}>{String(result)}</div>;
};
