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

import { Filter } from 'lucide-react';
import * as React from 'react';

import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { cn } from '@/lib/utils';

interface FilterSingleSelectProps<TValue> {
  title?: string;
  showTitle?: boolean;
  options: {
    label: string;
    value: TValue;
    icon?: React.ComponentType<{ className?: string }>;
  }[];
  selected?: TValue;
  setSelected: (selected: TValue | undefined) => void;
  align?: 'start' | 'end' | 'center';
}

const ALL_VALUE = '__all__';

export function FilterSingleSelect<TValue>({
  title,
  showTitle,
  options,
  selected,
  setSelected,
  align = 'start',
}: FilterSingleSelectProps<TValue>) {
  const radioValue = selected !== undefined ? String(selected) : ALL_VALUE;

  function handleChange(value: string) {
    if (value === ALL_VALUE) {
      setSelected(undefined);
    } else {
      const option = options.find((o) => String(o.value) === value);
      if (option) setSelected(option.value);
    }
  }

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button variant='ghost' size='narrow' className='font-medium'>
          {showTitle && <span className='text-sm'>{title}</span>}
          <Filter
            className={cn(
              'size-4',
              selected !== undefined ? 'text-blue-500' : undefined
            )}
          />
        </Button>
      </PopoverTrigger>
      <PopoverContent className='w-[200px] p-2' align={align}>
        <RadioGroup
          value={radioValue}
          onValueChange={handleChange}
          className='gap-1'
        >
          <div className='flex items-center gap-2 rounded-sm px-2 py-1.5'>
            <RadioGroupItem value={ALL_VALUE} id={`${title}-all`} />
            <Label
              htmlFor={`${title}-all`}
              className='cursor-pointer text-sm font-normal'
            >
              All
            </Label>
          </div>
          <div className='bg-border mx-2 h-px' />
          {options.map((option) => {
            const id = `${title}-${String(option.value)}`;
            return (
              <div
                key={String(option.value)}
                className='flex items-center gap-2 rounded-sm px-2 py-1.5'
              >
                <RadioGroupItem value={String(option.value)} id={id} />
                {option.icon && (
                  <option.icon className='text-muted-foreground h-4 w-4' />
                )}
                <Label
                  htmlFor={id}
                  className='cursor-pointer text-sm font-normal'
                >
                  {option.label}
                </Label>
              </div>
            );
          })}
        </RadioGroup>
      </PopoverContent>
    </Popover>
  );
}
