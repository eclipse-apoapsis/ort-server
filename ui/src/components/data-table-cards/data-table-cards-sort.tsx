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
import type { RowData } from '@tanstack/react-table';
import { Check, ChevronDown, ChevronsUpDown, ChevronUp } from 'lucide-react';
import { useId } from 'react';

import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { findMatchingSortPreset, SortPreset } from '@/helpers/sort-presets';
import type { AppReactTable, AppTableState } from '@/hooks/use-app-table';
import { cn } from '@/lib/utils';

const selectSortState = (state: AppTableState) => ({
  columnVisibility: state.columnVisibility,
  sorting: state.sorting,
});

/** Sortings that the sort menu offers to apply with one click, after the single columns. */
export interface SortPresetOptions {
  presets: SortPreset[];
  setPresetOptions: (preset: SortPreset) => LinkOptions;
}

interface DataTableCardsSortProps<TData extends RowData> {
  table: AppReactTable<TData>;
  setSortingOptions?: (sorting: {
    id: string;
    desc: boolean | undefined;
  }) => LinkOptions;
  sortPresets?: SortPresetOptions;
}

export function DataTableCardsSort<TData extends RowData>({
  table,
  setSortingOptions,
  sortPresets,
}: DataTableCardsSortProps<TData>) {
  const presetsLabelId = useId();

  if (!setSortingOptions) return null;

  return (
    <table.Subscribe selector={selectSortState}>
      {({ sorting }) => {
        // Invisible sorting columns are rendered in the card header menu. The sorted columns
        // come first, in the order they are sorted by, so that the menu shows which column
        // counts first; the other columns keep their order.
        const sortPosition = (columnId: string) => {
          const index = sorting.findIndex((sort) => sort.id === columnId);
          return index === -1 ? sorting.length : index;
        };
        const sortableColumns = table
          .getAllColumns()
          .filter((column) => !column.getIsVisible() && column.getCanSort())
          .sort((a, b) => sortPosition(a.id) - sortPosition(b.id));
        const sortedColumnCount = sortableColumns.filter((column) =>
          column.getIsSorted()
        ).length;

        if (sortableColumns.length === 0) return null;

        const isSortingActive = sorting.length > 0;
        const matchingPreset = sortPresets
          ? findMatchingSortPreset(sorting, sortPresets.presets)
          : undefined;

        return (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <div className='flex items-center gap-2'>
                <Button
                  variant='ghost'
                  size='narrow'
                  className='text-sm font-medium'
                >
                  <div className='text-sm font-medium'>Sort</div>
                  <ChevronDown
                    className={cn('size-4', isSortingActive && 'text-blue-500')}
                  />
                </Button>
              </div>
            </DropdownMenuTrigger>
            <DropdownMenuContent align='end'>
              <DropdownMenuGroup>
                {sortableColumns.map((column, index) => {
                  const isSorted = column.getIsSorted();

                  return (
                    <Tooltip key={column.id}>
                      <TooltipTrigger asChild>
                        <Link
                          {...setSortingOptions({
                            id: column.id,
                            desc:
                              isSorted === 'desc'
                                ? undefined
                                : isSorted === 'asc',
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
                            {isSorted && sortedColumnCount > 1 && (
                              // The position is also given by the order of the items, so it is
                              // hidden from screen readers to keep the item names unchanged.
                              <span
                                aria-hidden='true'
                                className='text-muted-foreground ml-auto pl-4 text-xs'
                              >
                                {index + 1}
                              </span>
                            )}
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
              {sortPresets && sortPresets.presets.length > 0 && (
                <DropdownMenuGroup aria-labelledby={presetsLabelId}>
                  <DropdownMenuSeparator />
                  <DropdownMenuLabel
                    id={presetsLabelId}
                    className='text-muted-foreground text-xs'
                  >
                    Presets
                  </DropdownMenuLabel>
                  {sortPresets.presets.map((preset) => {
                    const isMatching = preset.id === matchingPreset?.id;

                    return (
                      <Tooltip key={preset.id}>
                        <TooltipTrigger asChild>
                          <Link {...sortPresets.setPresetOptions(preset)}>
                            <DropdownMenuItem
                              aria-current={isMatching ? 'true' : undefined}
                            >
                              {isMatching ? (
                                <Check className='mr-2 h-4 w-4 text-blue-500' />
                              ) : (
                                <span className='mr-2 h-4 w-4' />
                              )}
                              <div>{preset.label}</div>
                            </DropdownMenuItem>
                          </Link>
                        </TooltipTrigger>
                        <TooltipContent>{preset.description}</TooltipContent>
                      </Tooltip>
                    );
                  })}
                </DropdownMenuGroup>
              )}
              {isSortingActive && (
                <DropdownMenuGroup>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem
                    className='justify-center text-center'
                    asChild
                  >
                    <Link {...setSortingOptions({ id: '', desc: undefined })}>
                      Clear Sorting
                    </Link>
                  </DropdownMenuItem>
                </DropdownMenuGroup>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        );
      }}
    </table.Subscribe>
  );
}
