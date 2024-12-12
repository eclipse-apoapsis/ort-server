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

import { Link, LinkOptions } from '@tanstack/react-router';
import { flexRender, GroupingState, HeaderGroup } from '@tanstack/react-table';
import { ChevronDown, ChevronsUpDown, ChevronUp, Group } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { TableHead, TableHeader, TableRow } from '@/components/ui/table';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';

interface DataTableHeaderProps<TData> {
  headerGroups: HeaderGroup<TData>[];
  groupingEnabled: boolean;
  setGroupingOptions?: (groups: GroupingState) => LinkOptions;
  setSortingOptions?: (sorting: {
    id: string;
    desc: boolean | undefined;
  }) => LinkOptions;
  groups: GroupingState;
}

export function DataTableHeader<TData>({
  headerGroups,
  groupingEnabled,
  setGroupingOptions,
  setSortingOptions,
  groups,
}: DataTableHeaderProps<TData>) {
  return (
    <TableHeader>
      {headerGroups.map((headerGroup) => (
        <TableRow key={headerGroup.id}>
          {headerGroup.headers.map((header) => {
            const { column } = header;

            const renderGroupButton = () => (
              <Tooltip>
                <TooltipTrigger asChild>
                  <Link
                    {...setGroupingOptions?.(
                      column.getIsGrouped()
                        ? groups.filter(
                            (group) =>
                              group !== column.columnDef.header?.toString()
                          )
                        : [...groups, column.columnDef.header?.toString() || '']
                    )}
                  >
                    <Button
                      variant={column.getIsGrouped() ? 'secondary' : 'ghost'}
                      className='-ml-2 px-2'
                      onClick={column.getToggleGroupingHandler()}
                    >
                      <div className='flex items-center gap-2'>
                        {flexRender(
                          column.columnDef.header,
                          header.getContext()
                        )}
                        <Group className='h-4 w-4' />
                      </div>
                    </Button>
                  </Link>
                </TooltipTrigger>
                <TooltipContent>
                  {column.getIsGrouped()
                    ? 'Toggle grouping off'
                    : 'Toggle grouping on'}
                </TooltipContent>
              </Tooltip>
            );

            const renderSortButton = () => (
              <Tooltip>
                <TooltipTrigger asChild>
                  <Link
                    {...setSortingOptions?.({
                      id: column.id,
                      desc:
                        column.getIsSorted() === 'desc'
                          ? undefined
                          : column.getIsSorted() === 'asc'
                            ? true
                            : false,
                    })}
                  >
                    <Button
                      variant='ghost'
                      className='-ml-2 px-2'
                      onClick={column.getToggleSortingHandler()}
                    >
                      <div className='flex items-center gap-2'>
                        {flexRender(
                          column.columnDef.header,
                          header.getContext()
                        )}
                        {column.getIsSorted() === 'asc' ? (
                          <ChevronUp className='h-4 w-4' />
                        ) : column.getIsSorted() === 'desc' ? (
                          <ChevronDown className='h-4 w-4' />
                        ) : (
                          <ChevronsUpDown className='h-4 w-4' />
                        )}
                      </div>
                    </Button>
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

            return (
              <TableHead
                key={header.id}
                style={{ minWidth: column.columnDef.size }}
                colSpan={header.colSpan}
              >
                {header.isPlaceholder
                  ? null
                  : groupingEnabled &&
                      setGroupingOptions &&
                      column.getCanGroup()
                    ? renderGroupButton()
                    : setSortingOptions && column.getCanSort()
                      ? renderSortButton()
                      : flexRender(
                          column.columnDef.header,
                          header.getContext()
                        )}
              </TableHead>
            );
          })}
        </TableRow>
      ))}
    </TableHeader>
  );
}
