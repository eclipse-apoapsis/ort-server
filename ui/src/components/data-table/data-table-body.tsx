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

import { flexRender, Row } from '@tanstack/react-table';
import { ChevronDown, ChevronRight } from 'lucide-react';
import React, { Fragment } from 'react';

import { Button } from '@/components/ui/button';
import { TableBody, TableCell, TableRow } from '@/components/ui/table';

interface DataTableBodyProps<TData> {
  rows: Row<TData>[];
  renderSubComponent?: (props: { row: Row<TData> }) => React.ReactElement;
}

export function DataTableBody<TData>({
  rows,
  renderSubComponent,
}: DataTableBodyProps<TData>) {
  return (
    <TableBody>
      {rows?.length ? (
        rows.map((row) => (
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
                    <Button
                      variant='ghost'
                      className='-ml-2 px-2'
                      onClick={row.getToggleExpandedHandler()}
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
                        )}{' '}
                        ({row.subRows.length})
                      </div>
                    </Button>
                  ) : cell.getIsAggregated() ? (
                    flexRender(
                      cell.column.columnDef.aggregatedCell ??
                        cell.column.columnDef.cell,
                      cell.getContext()
                    )
                  ) : cell.getIsPlaceholder() ? null : (
                    flexRender(cell.column.columnDef.cell, cell.getContext())
                  )}
                </TableCell>
              ))}
            </TableRow>
            {row.getIsExpanded() && renderSubComponent && (
              <TableRow>
                <TableCell colSpan={row.getVisibleCells().length}>
                  {renderSubComponent({ row })}
                </TableCell>
              </TableRow>
            )}
          </Fragment>
        ))
      ) : (
        <TableRow>
          <TableCell colSpan={rows.length || 1} className='h-24 text-center'>
            No results.
          </TableCell>
        </TableRow>
      )}
    </TableBody>
  );
}
