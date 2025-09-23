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
import { flexRender, Header } from '@tanstack/react-table';
import { ChevronDown, ChevronsUpDown, ChevronUp } from 'lucide-react';

import { DataTableFilter } from '@/components/data-table/data-table-filter';
import { Button } from '@/components/ui/button';
import { TableHead, TableHeader, TableRow } from '@/components/ui/table';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';

interface DataTableHeaderProps<TData> {
  headers: Header<TData, unknown>[];
  setSortingOptions?: (sorting: {
    id: string;
    desc: boolean | undefined;
  }) => LinkOptions;
}

export function DataTableHeader<TData>({
  headers,
  setSortingOptions,
}: DataTableHeaderProps<TData>) {
  return (
    <TableHeader>
      <TableRow>
        {headers.map((header) => {
          const { column } = header;

          const renderSortButton = () => (
            <Tooltip>
              <TooltipTrigger asChild>
                <Link
                  {...setSortingOptions?.({
                    id: column.id,
                    desc:
                      column.getIsSorted() === 'desc'
                        ? undefined
                        : column.getIsSorted() === 'asc',
                  })}
                >
                  <Button
                    variant='ghost'
                    size='narrow'
                    className='ml-4'
                    onClick={column.getToggleSortingHandler()}
                  >
                    {column.getIsSorted() === 'asc' ? (
                      <ChevronUp className='h-4 w-4 text-blue-500' />
                    ) : column.getIsSorted() === 'desc' ? (
                      <ChevronDown className='h-4 w-4 text-blue-500' />
                    ) : (
                      <ChevronsUpDown className='h-4 w-4' />
                    )}
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
              {header.isPlaceholder ? null : (
                <div className='flex items-center'>
                  {flexRender(column.columnDef.header, header.getContext())}
                  <div className='flex items-center'>
                    {setSortingOptions && column.getCanSort()
                      ? renderSortButton()
                      : null}
                    {column.getCanFilter() && (
                      <DataTableFilter column={column} />
                    )}
                  </div>
                </div>
              )}
            </TableHead>
          );
        })}
      </TableRow>
    </TableHeader>
  );
}
