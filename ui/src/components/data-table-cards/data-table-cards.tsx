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
import { Row, type Table as TanstackTable } from '@tanstack/react-table';
import React from 'react';

import { DataTableCardsHeader } from '@/components/data-table-cards/data-table-cards-header';
import { DataTableBody } from '@/components/data-table/data-table-body';
import { DataTablePagination } from '@/components/data-table/data-table-pagination';
import { Table } from '@/components/ui/table';
import { cn } from '@/lib/utils';

interface DataTableCardsProps<TData>
  extends React.HTMLAttributes<HTMLDivElement> {
  table: TanstackTable<TData>;
  renderSubComponent?: (props: { row: Row<TData> }) => React.ReactElement;
  setCurrentPageOptions: (page: number) => LinkOptions;
  setPageSizeOptions: (pageSize: number) => LinkOptions;
  setSortingOptions?: (sorting: {
    id: string;
    desc: boolean | undefined;
  }) => LinkOptions;
}

export function DataTableCards<TData>({
  table,
  renderSubComponent,
  className,
  setCurrentPageOptions,
  setPageSizeOptions,
  setSortingOptions,
  ...props
}: DataTableCardsProps<TData>) {
  const pagination = table.getState().pagination;
  const totalPages = table.getPageCount();

  return (
    <div
      className={cn('w-full space-y-2.5 overflow-auto', className)}
      {...props}
    >
      <DataTableCardsHeader
        table={table}
        setSortingOptions={setSortingOptions}
      />
      <Table>
        <DataTableBody
          rows={table.getRowModel().rows}
          renderSubComponent={renderSubComponent}
        />
      </Table>
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
