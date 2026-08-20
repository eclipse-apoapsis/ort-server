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

import { LinkOptions } from '@tanstack/react-router';
import type { RowData } from '@tanstack/react-table';
import React from 'react';

import { DataTablePagination } from '@/components/data-table/data-table-pagination';
import { Table } from '@/components/ui/table';
import type {
  AppReactTable,
  AppRow,
  AppTableState,
} from '@/hooks/use-app-table';
import { useTableSizing } from '@/hooks/use-table-sizing';
import { cn } from '@/lib/utils';
import { DataTableBody } from './data-table-body';
import { DataTableHeader } from './data-table-header';

export const DEFAULT_PAGE = 1;
export const DEFAULT_PAGE_SIZE = 10;

const selectBodyState = (state: AppTableState) => ({
  columnFilters: state.columnFilters,
  columnVisibility: state.columnVisibility,
  expanded: state.expanded,
  pagination: state.pagination,
  sorting: state.sorting,
});

const selectPaginationState = (state: AppTableState) => ({
  columnFilters: state.columnFilters,
  pagination: state.pagination,
  sorting: state.sorting,
});

interface DataTableProps<
  TData extends RowData,
> extends React.HTMLAttributes<HTMLDivElement> {
  table: AppReactTable<TData>;
  renderSubComponent?: (props: { row: AppRow<TData> }) => React.ReactElement;
  noResultsContent?: React.ReactNode;
  setCurrentPageOptions: (page: number) => LinkOptions;
  setPageSizeOptions: (pageSize: number) => LinkOptions;
  /**
   * A function to provide `LinkOptions` for a link to set current sorting in the URL.
   */
  setSortingOptions?: (sorting: {
    id: string;
    desc: boolean | undefined; // For column removal to work when multisorting, this needed to be changed
  }) => LinkOptions;
}

export function DataTable<TData extends RowData>({
  table,
  renderSubComponent,
  noResultsContent,
  className,
  setCurrentPageOptions,
  setPageSizeOptions,
  setSortingOptions,
  ...props
}: DataTableProps<TData>) {
  const { containerRef, columnSizing } = useTableSizing(table);

  return (
    <div
      ref={containerRef}
      className={cn('w-full space-y-2.5 overflow-auto', className)}
      {...props}
    >
      <Table style={{ tableLayout: 'fixed' }}>
        <DataTableHeader
          table={table}
          setSortingOptions={setSortingOptions}
          columnSizing={columnSizing}
        />
        <table.Subscribe selector={selectBodyState}>
          {() => {
            const rows = table.getRowModel().rows;

            return (
              <DataTableBody
                rows={rows}
                renderSubComponent={renderSubComponent}
                columnSizing={columnSizing}
                columnCount={table.getVisibleLeafColumns().length}
                noResultsContent={noResultsContent}
              />
            );
          }}
        </table.Subscribe>
      </Table>
      <table.Subscribe selector={selectPaginationState}>
        {({ pagination }) => {
          if (table.getRowModel().rows.length === 0) return null;

          return (
            <div className='flex flex-col gap-2.5'>
              <DataTablePagination
                currentPage={pagination.pageIndex + 1}
                pageSize={pagination.pageSize}
                totalPages={table.getPageCount()}
                setCurrentPageOptions={setCurrentPageOptions}
                setPageSizeOptions={setPageSizeOptions}
              />
            </div>
          );
        }}
      </table.Subscribe>
    </div>
  );
}
