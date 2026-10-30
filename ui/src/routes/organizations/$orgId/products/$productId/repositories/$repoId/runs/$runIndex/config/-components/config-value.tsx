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

import { CheckIcon, KeyRoundIcon, MinusIcon } from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';
import {
  ConfigPath,
  isEmpty,
  isMonospacePath,
  isNamedValueList,
  isSecretPath,
  isStringMap,
  lastKey,
  useListEntryName,
} from './config-utils';

export const NoneValue = () => (
  <span className='text-muted-foreground italic'>None</span>
);

export const SecretReference = ({ name }: { name: string }) => (
  <span className='inline-flex items-center gap-1.5'>
    <KeyRoundIcon
      aria-hidden
      className='text-muted-foreground size-3.5 shrink-0'
    />
    <span className='text-muted-foreground'>Secret</span>
    <code className='font-mono break-words'>{name}</code>
  </span>
);

const BooleanValue = ({ value }: { value: boolean }) => (
  <span className='inline-flex items-center gap-1.5'>
    {value ? (
      <CheckIcon aria-hidden className='size-4 text-green-600' />
    ) : (
      <MinusIcon aria-hidden className='text-muted-foreground size-4' />
    )}
    {value ? 'On' : 'Off'}
  </span>
);

const KeyValueList = ({
  entries,
}: {
  entries: { key: string; value: string; secret: boolean }[];
}) => (
  <dl className='grid grid-cols-[minmax(0,auto)_minmax(0,1fr)] gap-x-4 gap-y-1'>
    {entries.map(({ key, value, secret }) => (
      <div key={key} className='contents'>
        <dt className='font-mono break-words'>{key}</dt>
        <dd className='break-words'>
          {secret ? <SecretReference name={value} /> : value}
        </dd>
      </div>
    ))}
  </dl>
);

type ConfigValueProps = {
  value: unknown;
  path: ConfigPath;
};

/**
 * Show a value that fits in one row, choosing the presentation from its shape.
 */
export const ConfigValue = ({ value, path }: ConfigValueProps) => {
  const entryName = useListEntryName(path);

  if (isEmpty(value)) return <NoneValue />;

  if (typeof value === 'boolean') return <BooleanValue value={value} />;

  if (typeof value === 'string') {
    if (isSecretPath(path)) return <SecretReference name={value} />;

    return (
      <span className={cn('break-words', isMonospacePath(path) && 'font-mono')}>
        {value}
      </span>
    );
  }

  if (isNamedValueList(value)) {
    return (
      <KeyValueList
        entries={value.map((entry) => ({
          key: entry.name,
          value: entry.secretName ?? entry.value ?? '',
          secret: entry.secretName != null,
        }))}
      />
    );
  }

  if (Array.isArray(value)) {
    return (
      <ul className='flex flex-wrap gap-1.5'>
        {value.map((entry, index) => (
          <li key={index}>
            <Badge variant='secondary' className='font-normal'>
              {entryName(String(entry))}
            </Badge>
          </li>
        ))}
      </ul>
    );
  }

  if (isStringMap(value)) {
    const secret = lastKey(path) === 'secrets';
    return (
      <KeyValueList
        entries={Object.entries(value).map(([key, entry]) => ({
          key,
          value: entry,
          secret,
        }))}
      />
    );
  }

  return <span className='break-words'>{String(value)}</span>;
};
