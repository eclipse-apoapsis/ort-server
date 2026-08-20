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

import { DataTableCardsSort } from '@/components/data-table-cards/data-table-cards-sort';
import { DataTableFilter } from '@/components/data-table/data-table-filter';
import type { AppReactTable, AppTableState } from '@/hooks/use-app-table';

const selectFilterState = (state: AppTableState) => ({
  columnFilters: state.columnFilters,
  columnVisibility: state.columnVisibility,
});

interface DataTableCardsHeaderProps<TData extends RowData> {
  table: AppReactTable<TData>;
  setSortingOptions?: (sorting: {
    id: string;
    desc: boolean | undefined;
  }) => LinkOptions;
}

export function DataTableCardsHeader<TData extends RowData>({
  table,
  setSortingOptions,
}: DataTableCardsHeaderProps<TData>) {
  return (
    <div className='mb-0 flex items-center justify-end gap-6 border-b p-2'>
      <table.Subscribe selector={selectFilterState}>
        {() => {
          // Invisible filtering columns are rendered in the card header.
          const filterableColumns = table
            .getAllColumns()
            .filter(
              (column) => !column.getIsVisible() && column.getCanFilter()
            );

          return filterableColumns.map((column) => (
            <div className='flex items-center gap-2' key={column.id}>
              <DataTableFilter column={column} showTitle />
            </div>
          ));
        }}
      </table.Subscribe>

      <DataTableCardsSort table={table} setSortingOptions={setSortingOptions} />
    </div>
  );
}
