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

import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { expect, it } from 'vitest';

import {
  appTableFeatures,
  selectNoTableState,
  useAppTable,
  type AppColumnDef,
} from '@/hooks/use-app-table';

it('registers only the required table features and row models', () => {
  expect(Object.keys(appTableFeatures).sort()).toEqual([
    'columnFilteringFeature',
    'columnMeta',
    'columnSizingFeature',
    'columnVisibilityFeature',
    'paginatedRowModel',
    'rowExpandingFeature',
    'rowPaginationFeature',
    'rowSortingFeature',
    'sortedRowModel',
  ]);
});

it('sorts client-side rows by a controlled string accessor', () => {
  type PackageRow = { packageId: string };

  const data: PackageRow[] = [
    { packageId: 'pkg:z' },
    { packageId: 'pkg:a' },
    { packageId: 'pkg:m' },
  ];
  const columns: AppColumnDef<PackageRow>[] = [{ accessorKey: 'packageId' }];
  let sortedPackageIds: string[] = [];

  function TestTable() {
    const table = useAppTable(
      {
        data,
        columns,
        state: {
          pagination: { pageIndex: 0, pageSize: 10 },
          sorting: [{ id: 'packageId', desc: false }],
        },
        manualPagination: false,
        manualSorting: false,
      },
      selectNoTableState
    );

    sortedPackageIds = table
      .getRowModel()
      .rows.map((row) => row.original.packageId);

    return null;
  }

  renderToStaticMarkup(createElement(TestTable));

  expect(sortedPackageIds).toEqual(['pkg:a', 'pkg:m', 'pkg:z']);
});
