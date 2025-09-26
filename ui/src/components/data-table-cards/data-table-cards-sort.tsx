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

import { Link, LinkOptions } from '@tanstack/react-router';
import { Table } from '@tanstack/react-table';
import { ChevronDown, ChevronsUpDown, ChevronUp } from 'lucide-react';

import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';

interface DataTableCardsSortProps<TData> {
  table: Table<TData>;
  setSortingOptions?: (sorting: {
    id: string;
    desc: boolean | undefined;
  }) => LinkOptions;
}

export function DataTableCardsSort<TData>({
  table,
  setSortingOptions,
}: DataTableCardsSortProps<TData>) {
  const isSortingActive = table.getState().sorting?.length > 0;
  // Determine which columns are used as invisible sorting columns.
  // They will be shown in the sorting dropdown in the header.
  const sortableColumns = table
    .getAllColumns()
    .filter((column) => !column.getIsVisible() && column.getCanSort());

  if (!setSortingOptions || sortableColumns.length === 0) {
    return null;
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <div className='flex items-center gap-2'>
          <Button variant='ghost' size='narrow' className='text-sm font-medium'>
            <div className='text-sm font-medium'>Sort</div>
            <ChevronDown
              className={cn('size-4', isSortingActive && 'text-blue-500')}
            />
          </Button>
        </div>
      </DropdownMenuTrigger>
      <DropdownMenuContent align='end'>
        <DropdownMenuGroup>
          {sortableColumns.map((column) => {
            const isSorted = column.getIsSorted();

            return (
              <Tooltip key={column.id}>
                <TooltipTrigger asChild>
                  <Link
                    {...setSortingOptions({
                      id: column.id,
                      desc:
                        isSorted === 'desc' ? undefined : isSorted === 'asc',
                    })}
                  >
                    <DropdownMenuItem>
                      {isSorted === 'asc' ? (
                        <ChevronUp className='mr-2 h-4 w-4 text-blue-500' />
                      ) : isSorted === 'desc' ? (
                        <ChevronDown className='mr-2 h-4 w-4 text-blue-500' />
                      ) : (
                        <ChevronsUpDown className='mr-2 h-4 w-4' />
                      )}
                      <div>{column.columnDef.header?.toString()}</div>
                    </DropdownMenuItem>
                  </Link>
                </TooltipTrigger>
                <TooltipContent>
                  {column.getCanSort()
                    ? column.getIsSorted() === 'asc'
                      ? 'Sort descending'
                      : column.getIsSorted() === 'desc'
                        ? 'Clear sorting'
                        : 'Sort ascending'
                    : undefined}
                </TooltipContent>
              </Tooltip>
            );
          })}
        </DropdownMenuGroup>
        {isSortingActive && (
          <DropdownMenuGroup>
            <DropdownMenuSeparator />
            <DropdownMenuItem className='justify-center text-center' asChild>
              <Link {...setSortingOptions({ id: '', desc: undefined })}>
                Clear Sorting
              </Link>
            </DropdownMenuItem>
          </DropdownMenuGroup>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
