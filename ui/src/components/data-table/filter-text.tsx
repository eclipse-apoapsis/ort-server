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

import { Filter, XCircle } from 'lucide-react';
import { useEffect, useState } from 'react';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover';
import { cn } from '@/lib/utils';

interface FilterTextProps {
  title?: string;
  showTitle?: boolean; // Whether to show the title next to the filter icon
  filterValue: string;
  setFilterValue: (value: string | undefined) => void;
}

export function FilterText({
  title,
  showTitle,
  filterValue: initialValue,
  setFilterValue,
}: FilterTextProps) {
  const [value, setValue] = useState(initialValue);
  const [filterOpen, setFilterOpen] = useState(false);

  useEffect(() => {
    setValue(initialValue);
  }, [initialValue]);

  return (
    <Popover open={filterOpen} onOpenChange={setFilterOpen}>
      <PopoverTrigger asChild>
        <Button variant='ghost' size='narrow'>
          {showTitle && <span className='text-sm'>{title}</span>}
          <Filter
            className={cn('size-4', value.length > 0 && 'text-blue-500')}
          />
        </Button>
      </PopoverTrigger>
      <PopoverContent>
        <div className='flex gap-2'>
          <form
            onSubmit={(event) => {
              event.preventDefault();
              setFilterValue(value);
              setFilterOpen(false);
            }}
          >
            <Input
              placeholder='(case-insensitive substring)'
              value={value}
              onBlur={() => setFilterValue(value)}
              onChange={(event) => {
                setValue(event.target.value);
              }}
            />
          </form>
          <Button
            variant='ghost'
            className='px-2'
            onClick={() => {
              setFilterValue(undefined);
              setFilterOpen(false);
            }}
          >
            <XCircle
              className={cn(
                'h-fit text-gray-400',
                value.length === 0 ? 'opacity-40' : 'opacity-100'
              )}
            />
            <span className='sr-only'>Clear search</span>
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  );
}
