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

import debounce from 'debounce';
import { Filter, XCircle } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover';
import { cn } from '@/lib/utils';

interface FilterTextProps {
  filterValue: string;
  setFilterValue: (value: string | undefined) => void;
}

export function FilterText({
  filterValue: initialValue,
  setFilterValue,
}: FilterTextProps) {
  const [value, setValue] = useState(initialValue);

  const debounceSetFilterValue = useMemo(
    () => debounce(setFilterValue, 500),
    [setFilterValue]
  );

  useEffect(() => {
    setValue(initialValue);
  }, [initialValue]);

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button variant='ghost' className='px-1'>
          <Filter
            className={cn(value.length > 0 && 'text-blue-500', 'h-4 w-4')}
          />
        </Button>
      </PopoverTrigger>
      <PopoverContent>
        <div className='flex gap-2'>
          <Input
            value={value}
            onChange={(event) => {
              setValue(event.target.value);
              if (event.target.value.length === 0) {
                debounceSetFilterValue(undefined);
              } else {
                debounceSetFilterValue(event.target.value);
              }
            }}
          />
          <Button
            variant='ghost'
            className='px-2'
            onClick={() => setFilterValue(undefined)}
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
