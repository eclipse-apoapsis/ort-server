/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import { Check, Filter } from 'lucide-react';
import * as React from 'react';

import { Button } from '@/components/ui/button';
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator,
} from '@/components/ui/command';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover';
import { cn } from '@/lib/utils';

type FilterOption<TValue> = {
  label: string;
  value: TValue;
  icon?: React.ComponentType<{ className?: string }>;
  group?: string;
};

interface FilterMultiSelectProps<TValue> {
  title?: string;
  showTitle?: boolean; // Whether to show the title next to the filter icon
  options: FilterOption<TValue>[];
  selected: TValue[];
  setSelected: (selected: TValue[]) => void;
  align?: 'start' | 'end' | 'center';
}

export function FilterMultiSelect<TValue>({
  title,
  showTitle,
  options,
  selected,
  setSelected,
  align = 'start',
}: FilterMultiSelectProps<TValue>) {
  const optionGroups = options.reduce<
    { group?: string; options: FilterOption<TValue>[] }[]
  >((groups, option) => {
    const previousGroup = groups.at(-1);

    if (previousGroup && previousGroup.group === option.group) {
      previousGroup.options.push(option);
    } else {
      groups.push({ group: option.group, options: [option] });
    }

    return groups;
  }, []);

  const renderOption = (option: FilterOption<TValue>) => {
    const isSelected = selected.includes(option.value);

    return (
      <CommandItem
        key={String(option.value)}
        value={option.label}
        onSelect={() => {
          if (isSelected) {
            setSelected(selected.filter((value) => value !== option.value));
          } else {
            setSelected([...selected, option.value]);
          }
        }}
      >
        <div
          className={cn(
            'border-primary mr-2 flex h-4 w-4 items-center justify-center rounded-sm border',
            isSelected
              ? 'bg-primary text-primary-foreground'
              : 'opacity-50 [&_svg]:invisible'
          )}
        >
          <Check className={cn('h-4 w-4')} />
        </div>
        {option.icon && (
          <option.icon className='text-muted-foreground mr-2 h-4 w-4' />
        )}
        <span>{option.label}</span>
      </CommandItem>
    );
  };

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button variant='ghost' size='narrow' className='font-medium'>
          {showTitle && <span className='text-sm'>{title}</span>}
          <Filter
            className={cn(
              'size-4',
              selected.length > 0 ? 'text-blue-500' : undefined
            )}
          />
        </Button>
      </PopoverTrigger>
      <PopoverContent className='w-[200px] p-0' align={align}>
        <Command
          filter={(value, search) => {
            const normalizedValue = value.toLowerCase();
            const normalizedSearch = search.trim().toLowerCase();

            if (!normalizedValue.includes(normalizedSearch)) {
              return 0;
            }

            if (normalizedValue === normalizedSearch) {
              return 1;
            }

            if (normalizedValue.startsWith(normalizedSearch)) {
              return 0.9;
            }

            return 0.8;
          }}
        >
          {options.length > 5 && <CommandInput placeholder={title} />}
          <CommandList>
            <CommandEmpty>No results found.</CommandEmpty>
            {optionGroups.map((group, index) => (
              <React.Fragment key={group.group ?? `group-${index}`}>
                {index > 0 && <CommandSeparator />}
                <CommandGroup heading={group.group}>
                  {group.options.map(renderOption)}
                </CommandGroup>
              </React.Fragment>
            ))}
            {selected.length > 0 && (
              <>
                <CommandSeparator />
                <CommandGroup>
                  <CommandItem
                    onSelect={() => setSelected([])}
                    className='justify-center text-center'
                  >
                    Clear selection
                  </CommandItem>
                </CommandGroup>
              </>
            )}
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
