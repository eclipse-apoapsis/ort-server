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

// @vitest-environment jsdom

import type { LinkOptions } from '@tanstack/react-router';
import type { ExpandedState, SortingState } from '@tanstack/react-table';
import { screen } from '@testing-library/react';
import { useState, type ReactNode } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import { DataTableCards } from '@/components/data-table-cards/data-table-cards';
import { DataTableCardsHeader } from '@/components/data-table-cards/data-table-cards-header';
import { DataTableCardsSort } from '@/components/data-table-cards/data-table-cards-sort';
import { Button } from '@/components/ui/button';
import { EMPTY_SORTING_STATE } from '@/helpers/handle-multisort';
import {
  selectNoTableState,
  useAppTable,
  type AppColumnDef,
  type AppReactTable,
} from '@/hooks/use-app-table';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

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

type SortOrderRow = {
  first: string;
  second: string;
  third: string;
};

const SortMenuOrderHarness = ({ sorting }: { sorting: SortingState }) => {
  const table = useAppTable(
    {
      data: [{ first: 'a', second: 'b', third: 'c' }] as SortOrderRow[],
      columns: [
        { accessorKey: 'first', header: 'First' },
        { accessorKey: 'second', header: 'Second' },
        { accessorKey: 'third', header: 'Third' },
      ],
      state: {
        columnVisibility: { first: false, second: false, third: false },
        sorting,
      },
    },
    selectNoTableState
  );

  return (
    <DataTableCardsSort table={table} setSortingOptions={setSortingOptions} />
  );
};

const openSortMenu = async (sorting: SortingState) => {
  const { user } = renderInteractiveWithRouter(
    <SortMenuOrderHarness sorting={sorting} />,
    { path: '/sort', routes: [{ path: '/sort' }] }
  );
  await user.click(await screen.findByRole('button', { name: 'Sort' }));

  return screen.findAllByRole('menuitem');
};

describe('DataTableCardsSort column order', () => {
  it('lists the sorted columns first, in sort order, with their positions', async () => {
    const items = await openSortMenu([
      { id: 'third', desc: true },
      { id: 'first', desc: false },
    ]);

    expect(items.map((item) => item.textContent)).toEqual([
      'Third1',
      'First2',
      'Second',
      'Reset',
    ]);
  });

  it('shows no position when only one column is sorted', async () => {
    const items = await openSortMenu([{ id: 'second', desc: false }]);

    expect(items.map((item) => item.textContent)).toEqual([
      'Second',
      'First',
      'Third',
      'Reset',
    ]);
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

const SortMenuWithPresetsHarness = () => {
  const table = useAppTable(
    {
      data,
      columns: [hiddenSortColumn],
      state: {
        columnVisibility: { sort: false },
        sorting: [{ id: 'sort', desc: true }],
      },
    },
    selectNoTableState
  );

  return (
    <DataTableCardsSort
      table={table}
      setSortingOptions={setSortingOptions}
      sortPresets={{
        presets: [
          {
            id: 'ascending',
            label: 'Ascending preset',
            description: 'Sort ascending.',
            sorting: [{ id: 'sort', desc: false }],
          },
          {
            id: 'descending',
            label: 'Descending preset',
            description: 'Sort descending.',
            sorting: [{ id: 'sort', desc: true }],
          },
        ],
        setPresetOptions: () => ({ to: '/' }),
      }}
    />
  );
};

describe('DataTableCardsSort with presets', () => {
  it('lists the presets after the columns and marks the matching one', async () => {
    const { user } = renderInteractiveWithRouter(
      <SortMenuWithPresetsHarness />,
      { path: '/sort', routes: [{ path: '/sort' }] }
    );

    await user.click(await screen.findByRole('button', { name: 'Sort' }));

    const items = await screen.findAllByRole('menuitem');
    expect(items.map((item) => item.textContent)).toEqual([
      'Hidden sort',
      'Ascending preset',
      'Descending preset',
      'Reset',
    ]);
    expect(items[1]).not.toHaveAttribute('aria-current');
    expect(items[2]).toHaveAttribute('aria-current', 'true');
    expect(screen.getByRole('group', { name: 'Presets' })).toContainElement(
      items[1] ?? null
    );
  });
});

type CardRow = {
  category: string;
  name: string;
};

const cardRows: CardRow[] = [
  { category: 'Example category', name: 'First project' },
  { category: 'Example category', name: 'Second project' },
];

const cardColumns: AppColumnDef<CardRow>[] = [
  {
    id: 'details',
    cell: ({ row }) => (
      <Button
        aria-label={`${row.getIsExpanded() ? 'Collapse' : 'Expand'} ${row.original.name}`}
        onClick={row.getToggleExpandedHandler()}
      >
        Details
      </Button>
    ),
  },
  {
    id: 'card',
    cell: ({ row }) => row.original.name,
  },
  {
    accessorKey: 'name',
    header: 'Name',
  },
  {
    accessorKey: 'category',
    header: 'Category',
  },
];

// Controls the expanded state like the finding views do, and sorts by the
// empty default state, with which expanding a card once failed. The refresh
// button replaces the rows with equal copies, as a refetch of the page would.
const CardTableHarness = () => {
  const [data, setData] = useState(cardRows);
  const [expanded, setExpanded] = useState<ExpandedState>({});
  const table = useAppTable(
    {
      columns: cardColumns,
      data,
      pageCount: 1,
      state: {
        columnFilters: [],
        columnVisibility: { category: false, name: false },
        expanded,
        pagination: { pageIndex: 0, pageSize: 10 },
        sorting: EMPTY_SORTING_STATE,
      },
      onExpandedChange: setExpanded,
      getRowCanExpand: () => true,
      getRowId: (row) => row.name,
      manualFiltering: true,
      manualPagination: true,
      manualSorting: true,
    },
    selectNoTableState
  );

  return (
    <>
      <Button onClick={() => setData(data.map((row) => ({ ...row })))}>
        Refresh
      </Button>
      <DataTableCards
        table={table}
        renderSubComponent={({ row }) => <div>{row.original.name} details</div>}
        setCurrentPageOptions={() => ({ to: '/' })}
        setPageSizeOptions={() => ({ to: '/' })}
        setSortingOptions={() => ({ to: '/' })}
      />
    </>
  );
};

const renderCardTable = () =>
  renderInteractiveWithRouter(<CardTableHarness />, {
    path: '/cards',
    routes: [{ path: '/cards' }],
  });

describe('DataTableCards', () => {
  it('expands a card on its own and collapses it again', async () => {
    const { user } = renderCardTable();

    await user.click(
      await screen.findByRole('button', { name: 'Expand First project' })
    );

    expect(await screen.findByText('First project details')).toBeVisible();
    expect(
      screen.queryByText('Second project details')
    ).not.toBeInTheDocument();

    await user.click(
      screen.getByRole('button', { name: 'Collapse First project' })
    );

    expect(screen.queryByText('First project details')).not.toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Expand First project' })
    ).toBeVisible();
  });

  it('collapses an expanded card when the table receives new data', async () => {
    const { user } = renderCardTable();
    await user.click(
      await screen.findByRole('button', { name: 'Expand Second project' })
    );
    expect(await screen.findByText('Second project details')).toBeVisible();

    await user.click(screen.getByRole('button', { name: 'Refresh' }));

    expect(
      screen.queryByText('Second project details')
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Expand Second project' })
    ).toBeVisible();
  });
});
