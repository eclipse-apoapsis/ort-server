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

import { PlusIcon } from 'lucide-react';
import { useState } from 'react';

import { Button } from '@/components/ui/button';
import {
  Command,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover';
import {
  guessReason,
  type IncludeExcludeDirection,
  type IncludeExcludeEntry,
  type IncludeExcludeKind,
} from './includes-excludes';

type PatternComboboxProps = {
  kind: IncludeExcludeKind;
  onAdd: (entry: IncludeExcludeEntry) => void;
};

const texts = {
  scopes: {
    button: 'Add scope pattern',
    placeholder: 'Type a regular expression',
    exclude: 'Exclude scopes matching',
    include: 'Include only scopes matching',
  },
  paths: {
    button: 'Add path pattern',
    placeholder: 'Type a glob pattern',
    exclude: 'Exclude paths matching',
    include: 'Include only paths matching',
  },
} as const;

/** A button that opens a list for adding an include or exclude from a typed pattern. */
export const PatternCombobox = ({ kind, onAdd }: PatternComboboxProps) => {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const pattern = search.trim();

  const add = (direction: IncludeExcludeDirection) => {
    onAdd({
      direction,
      pattern,
      reason: guessReason(kind, direction, pattern),
      comment: '',
    });
    setSearch('');
    setOpen(false);
  };

  return (
    <Popover
      open={open}
      onOpenChange={(isOpen) => {
        setOpen(isOpen);
        if (!isOpen) setSearch('');
      }}
    >
      <PopoverTrigger asChild>
        <Button size='sm' variant='outline' type='button' className='w-fit'>
          {texts[kind].button}
          <PlusIcon className='ml-1 h-4 w-4' />
        </Button>
      </PopoverTrigger>
      <PopoverContent
        className='w-[420px] max-w-[calc(100vw-2rem)] p-0'
        align='start'
      >
        <Command shouldFilter={false}>
          <CommandInput
            placeholder={texts[kind].placeholder}
            value={search}
            onValueChange={setSearch}
            className='font-mono'
          />
          <CommandList>
            {pattern === '' ? (
              <div className='text-muted-foreground px-3 py-4 text-sm'>
                Type a pattern to add it.
              </div>
            ) : (
              <CommandGroup heading='Custom pattern'>
                {(['exclude', 'include'] as const).map((direction) => (
                  <CommandItem
                    key={direction}
                    value={direction}
                    onSelect={() => add(direction)}
                  >
                    <span className='min-w-0 break-all'>
                      {texts[kind][direction]}{' '}
                      <code className='font-mono'>{pattern}</code>
                    </span>
                  </CommandItem>
                ))}
              </CommandGroup>
            )}
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
};
