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

import type { LinkOptions } from '@tanstack/react-router';
import type { SortingState } from '@tanstack/react-table';
import type { ReactNode } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import { DataTableCardsHeader } from '@/components/data-table-cards/data-table-cards-header';
import { DataTableCardsSort } from '@/components/data-table-cards/data-table-cards-sort';
import {
  selectNoTableState,
  useAppTable,
  type AppColumnDef,
  type AppReactTable,
} from '@/hooks/use-app-table';

type TestRow = {
  filter: string;
  sort: string;
};

type TableHarnessOptions = {
  columns: AppColumnDef<TestRow>[];
  columnVisibility: Record<string, boolean>;
  sorting: SortingState | undefined;
};

const data: TestRow[] = [{ filter: 'filter value', sort: 'sort value' }];

const hiddenFilterColumn: AppColumnDef<TestRow> = {
  accessorKey: 'filter',
  header: 'Hidden filter',
  enableSorting: false,
  meta: {
    filter: {
      filterVariant: 'text',
      setFilterValue: () => {},
    },
  },
};

const hiddenSortColumn: AppColumnDef<TestRow> = {
  accessorKey: 'sort',
  header: 'Hidden sort',
  enableColumnFilter: false,
};

const setSortingOptions = (): LinkOptions => ({ to: '/' });

function renderWithTable(
  children: (table: AppReactTable<TestRow>) => ReactNode,
  { columns, columnVisibility, sorting }: TableHarnessOptions
) {
  function TableHarness() {
    const table = useAppTable(
      {
        data,
        columns,
        state: {
          columnVisibility,
          sorting,
        },
      },
      selectNoTableState
    );

    return children(table);
  }

  return renderToStaticMarkup(<TableHarness />);
}

describe('DataTableCardsHeader', () => {
  it('renders hidden column filters and sorting controls', () => {
    const markup = renderWithTable(
      (table) => (
        <DataTableCardsHeader
          table={table}
          setSortingOptions={setSortingOptions}
        />
      ),
      {
        columns: [hiddenFilterColumn, hiddenSortColumn],
        columnVisibility: { filter: false, sort: false },
        sorting: [],
      }
    );

    expect(markup).toContain('Hidden filter');
    expect(markup).toContain('>Sort<');
  });
});

// Dropdown content stays closed during static rendering, so its real Link
// descendants are not mounted and do not require a router or a Link mock.
describe('DataTableCardsSort', () => {
  it('renders nothing without sorting link options', () => {
    const markup = renderWithTable(
      (table) => <DataTableCardsSort table={table} />,
      {
        columns: [hiddenSortColumn],
        columnVisibility: { sort: false },
        sorting: [],
      }
    );

    expect(markup).toBe('');
  });

  it('renders nothing without sortable hidden columns', () => {
    const markup = renderWithTable(
      (table) => (
        <DataTableCardsSort
          table={table}
          setSortingOptions={setSortingOptions}
        />
      ),
      {
        columns: [hiddenFilterColumn],
        columnVisibility: { filter: false },
        sorting: [],
      }
    );

    expect(markup).toBe('');
  });

  it('renders the sort trigger for a sortable hidden column', () => {
    const markup = renderWithTable(
      (table) => (
        <DataTableCardsSort
          table={table}
          setSortingOptions={setSortingOptions}
        />
      ),
      {
        columns: [hiddenSortColumn],
        columnVisibility: { sort: false },
        sorting: [],
      }
    );

    expect(markup).toContain('>Sort<');
  });

  it('renders with undefined sorting state', () => {
    const markup = renderWithTable(
      (table) => (
        <DataTableCardsSort
          table={table}
          setSortingOptions={setSortingOptions}
        />
      ),
      {
        columns: [hiddenSortColumn],
        columnVisibility: { sort: false },
        sorting: undefined,
      }
    );

    expect(markup).toContain('>Sort<');
  });
});
