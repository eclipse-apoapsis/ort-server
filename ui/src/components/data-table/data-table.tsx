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
import {
  flexRender,
  GroupingState,
  Row,
  type Table as TanstackTable,
} from '@tanstack/react-table';
import { ChevronDown, ChevronRight, Group } from 'lucide-react';
import * as React from 'react';
import { Fragment } from 'react';

import { DataTablePagination } from '@/components/data-table/data-table-pagination';
import { Button } from '@/components/ui/button';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';

interface DataTableProps<TData> extends React.HTMLAttributes<HTMLDivElement> {
  table: TanstackTable<TData>;
  renderSubComponent?: (props: { row: Row<TData> }) => React.ReactElement;
  setCurrentPageOptions: (page: number) => LinkOptions;
  setPageSizeOptions: (pageSize: number) => LinkOptions;
  /**
   * A function to provide `LinkOptions` for a link to set current selected groups in the URL.
   */
  setGroupingOptions?: (groups: GroupingState) => LinkOptions;
  enableGrouping?: boolean;
}

export function DataTable<TData>({
  table,
  renderSubComponent,
  children,
  className,
  setCurrentPageOptions,
  setPageSizeOptions,
  setGroupingOptions,
  enableGrouping,
  ...props
}: DataTableProps<TData>) {
  const pagination = table.getState().pagination;
  const totalPages = table.getPageCount();
  const groups = table.getState().grouping;
  const groupingEnabled = enableGrouping || false;

  return (
    <div
      className={cn('w-full space-y-2.5 overflow-auto', className)}
      {...props}
    >
      {children}
      <div>
        <Table>
          <TableHeader>
            {table.getHeaderGroups().map((headerGroup) => (
              <TableRow key={headerGroup.id}>
                {headerGroup.headers.map((header) => {
                  return (
                    <TableHead
                      key={header.id}
                      style={{ minWidth: header.column.columnDef.size }}
                      colSpan={header.colSpan}
                    >
                      {header.isPlaceholder ? null : (
                        <div>
                          {groupingEnabled &&
                          setGroupingOptions &&
                          header.column.getCanGroup() ? (
                            <Tooltip>
                              <TooltipTrigger asChild>
                                <Link
                                  {...setGroupingOptions(
                                    header.column.getIsGrouped()
                                      ? groups.filter(
                                          (group) =>
                                            group !==
                                            header.column.columnDef.header?.toString()
                                        ) // Remove the column from groups if it's grouped
                                      : [
                                          ...groups,
                                          header.column.columnDef.header?.toString() ||
                                            '',
                                        ] // Add the column to groups if it's not already grouped
                                  )}
                                >
                                  <Button
                                    variant={
                                      header.column.getIsGrouped()
                                        ? 'secondary'
                                        : 'ghost'
                                    }
                                    className='-ml-2 px-2'
                                    {...{
                                      onClick:
                                        header.column.getToggleGroupingHandler(),
                                      style: {
                                        cursor: 'pointer',
                                      },
                                    }}
                                  >
                                    <div className='flex items-center gap-2'>
                                      {flexRender(
                                        header.column.columnDef.header,
                                        header.getContext()
                                      )}
                                      <Group className='h-4 w-4' />
                                    </div>
                                  </Button>
                                </Link>
                              </TooltipTrigger>
                              <TooltipContent>
                                {header.column.getIsGrouped()
                                  ? 'Toggle grouping off'
                                  : 'Toggle grouping on'}
                              </TooltipContent>
                            </Tooltip>
                          ) : (
                            <>
                              {flexRender(
                                header.column.columnDef.header,
                                header.getContext()
                              )}
                            </>
                          )}
                        </div>
                      )}
                    </TableHead>
                  );
                })}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {table.getRowModel().rows?.length ? (
              table.getRowModel().rows.map((row) => (
                <Fragment key={row.id}>
                  <TableRow
                    data-state={row.getIsSelected() && 'selected'}
                    className={
                      row.getIsExpanded() && renderSubComponent
                        ? 'border-0'
                        : undefined
                    }
                  >
                    {row.getVisibleCells().map((cell) => (
                      <TableCell
                        key={cell.id}
                        style={{ minWidth: cell.column.columnDef.size }}
                      >
                        {cell.getIsGrouped() ? (
                          // If it's a grouped cell, add an expander and row count
                          <Button
                            variant='ghost'
                            className='-ml-2 px-2'
                            {...{
                              onClick: row.getToggleExpandedHandler(),
                            }}
                          >
                            <div className='flex items-center gap-2'>
                              {row.getIsExpanded() ? (
                                <ChevronDown className='h-4 w-4' />
                              ) : (
                                <ChevronRight className='h-4 w-4' />
                              )}
                              {flexRender(
                                cell.column.columnDef.cell,
                                cell.getContext()
                              )}
                              ({row.subRows.length})
                            </div>
                          </Button>
                        ) : cell.getIsAggregated() ? (
                          // If the cell is aggregated, use the Aggregated
                          // renderer for cell. This is for possible future
                          // aggregated information for grouped tables
                          flexRender(
                            cell.column.columnDef.aggregatedCell ??
                              cell.column.columnDef.cell,
                            cell.getContext()
                          )
                        ) : cell.getIsPlaceholder() ? null : ( // For cells with repeated values, render null
                          // Otherwise, just render the regular cell
                          flexRender(
                            cell.column.columnDef.cell,
                            cell.getContext()
                          )
                        )}
                      </TableCell>
                    ))}
                  </TableRow>
                  {row.getIsExpanded() && renderSubComponent && (
                    <TableRow>
                      {/* 2nd row is a custom 1 cell row */}
                      <TableCell colSpan={row.getVisibleCells().length}>
                        {renderSubComponent({ row })}
                      </TableCell>
                    </TableRow>
                  )}
                </Fragment>
              ))
            ) : (
              <TableRow>
                <TableCell
                  colSpan={table.getAllColumns().length}
                  className='h-24 text-center'
                >
                  No results.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
      {table.getRowModel().rows?.length > 0 && (
        <div className='flex flex-col gap-2.5'>
          <DataTablePagination
            currentPage={pagination.pageIndex + 1}
            pageSize={pagination.pageSize}
            totalPages={totalPages}
            setCurrentPageOptions={setCurrentPageOptions}
            setPageSizeOptions={setPageSizeOptions}
          />
        </div>
      )}
    </div>
  );
}
