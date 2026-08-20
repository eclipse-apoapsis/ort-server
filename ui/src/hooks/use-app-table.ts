/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import {
  columnFilteringFeature,
  columnSizingFeature,
  columnVisibilityFeature,
  createPaginatedRowModel,
  createSortedRowModel,
  createTableHook,
  metaHelper,
  rowExpandingFeature,
  rowPaginationFeature,
  rowSortingFeature,
  tableFeatures,
  type CellContext,
  type Column,
  type ColumnDef,
  type Header,
  type ReactTable,
  type Row,
  type RowData,
  type Table,
  type TableState,
} from '@tanstack/react-table';

import type { DataTableColumnMeta } from '@/components/data-table/data-table-types';

export const appTableFeatures = tableFeatures({
  columnFilteringFeature,
  columnSizingFeature,
  columnVisibilityFeature,
  rowExpandingFeature,
  rowPaginationFeature,
  rowSortingFeature,
  sortedRowModel: createSortedRowModel(),
  paginatedRowModel: createPaginatedRowModel(),
  columnMeta: metaHelper<DataTableColumnMeta>(),
});

export type AppTableFeatures = typeof appTableFeatures;
export type AppTableState = TableState<AppTableFeatures>;
export type AppReactTable<
  TData extends RowData,
  TSelected = undefined,
> = ReactTable<AppTableFeatures, TData, TSelected>;
export type AppTable<TData extends RowData> = Table<AppTableFeatures, TData>;
export type AppRow<TData extends RowData> = Row<AppTableFeatures, TData>;
export type AppColumn<TData extends RowData, TValue = unknown> = Column<
  AppTableFeatures,
  TData,
  TValue
>;
export type AppHeader<TData extends RowData, TValue = unknown> = Header<
  AppTableFeatures,
  TData,
  TValue
>;
export type AppColumnDef<TData extends RowData, TValue = unknown> = ColumnDef<
  AppTableFeatures,
  TData,
  TValue
>;
export type AppCellContext<
  TData extends RowData,
  TValue = unknown,
> = CellContext<AppTableFeatures, TData, TValue>;

export const selectNoTableState: (state: AppTableState) => undefined = () =>
  undefined;

export const { useAppTable, createAppColumnHelper } = createTableHook({
  features: appTableFeatures,
  manualSorting: true,
  manualPagination: true,
});
