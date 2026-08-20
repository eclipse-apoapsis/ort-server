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

import { LinkOptions } from '@tanstack/react-router';
import type { RowData } from '@tanstack/react-table';
import React from 'react';

import { DataTableCardsHeader } from '@/components/data-table-cards/data-table-cards-header';
import { DataTableBody } from '@/components/data-table/data-table-body';
import { DataTablePagination } from '@/components/data-table/data-table-pagination';
import { Table } from '@/components/ui/table';
import type {
  AppReactTable,
  AppRow,
  AppTableState,
} from '@/hooks/use-app-table';
import { useTableSizing } from '@/hooks/use-table-sizing';
import { cn } from '@/lib/utils';

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

interface DataTableCardsProps<
  TData extends RowData,
> extends React.HTMLAttributes<HTMLDivElement> {
  table: AppReactTable<TData>;
  renderSubComponent?: (props: { row: AppRow<TData> }) => React.ReactElement;
  noResultsContent?: React.ReactNode;
  setCurrentPageOptions: (page: number) => LinkOptions;
  setPageSizeOptions: (pageSize: number) => LinkOptions;
  setSortingOptions?: (sorting: {
    id: string;
    desc: boolean | undefined;
  }) => LinkOptions;
}

export function DataTableCards<TData extends RowData>({
  table,
  renderSubComponent,
  noResultsContent,
  className,
  setCurrentPageOptions,
  setPageSizeOptions,
  setSortingOptions,
  ...props
}: DataTableCardsProps<TData>) {
  const { containerRef, columnSizing } = useTableSizing(table);

  return (
    <div
      ref={containerRef}
      className={cn('w-full space-y-2.5 overflow-auto', className)}
      {...props}
    >
      <DataTableCardsHeader
        table={table}
        setSortingOptions={setSortingOptions}
      />
      <Table style={{ tableLayout: 'fixed' }}>
        <table.Subscribe selector={selectBodyState}>
          {() => {
            const rows = table.getRowModel().rows;

            return (
              <DataTableBody
                rows={rows}
                renderSubComponent={renderSubComponent}
                columnSizing={columnSizing}
                noResultsContent={noResultsContent}
                columnCount={table.getVisibleLeafColumns().length}
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
