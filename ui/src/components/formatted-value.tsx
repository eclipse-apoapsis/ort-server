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
  value: T | T[] | null | undefined;
  type?: 'string' | 'array' | 'url';
};

export const FormattedValue = <T,>({
  value,
  type = 'string',
}: FormattedValueProps<T>) => {
  const arrayFormat = type === 'array' ? 'array' : 'string';
  const result = valueOrNa(value, { arrayFormat });
  const isNa = result === 'N/A';

  // Render array as vertical list or "N/A" with indentation
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

    // "N/A" for empty arrays, indented like array items
    return <div className='text-muted-foreground ml-2'>{String(result)}</div>;
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
  return (
    <span className={isNa ? 'text-muted-foreground' : undefined}>
      {String(result)}
    </span>
  );
};
