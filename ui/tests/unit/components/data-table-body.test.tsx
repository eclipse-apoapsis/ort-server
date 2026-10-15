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
import { act, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { DataTable } from '@/components/data-table/data-table';
import {
  selectNoTableState,
  useAppTable,
  type AppColumnDef,
  type AppReactTable,
  type AppRow,
} from '@/hooks/use-app-table';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

type TestRow = {
  name: string;
  first: string;
  second: string;
};

const data: TestRow[] = [{ name: 'core', first: 'First', second: 'Second' }];

const columns: AppColumnDef<TestRow>[] = [
  {
    id: 'expand',
    header: 'Expand',
    cell: ({ row }) => (
      <button type='button' onClick={row.getToggleExpandedHandler()}>
        Toggle {row.original.name}
      </button>
    ),
  },
  { accessorKey: 'first', header: 'First column' },
  { accessorKey: 'second', header: 'Second column' },
];

const renderSubComponent = ({ row }: { row: AppRow<TestRow> }) => (
  <div>Details of {row.original.name}</div>
);

const linkOptions = (): LinkOptions => ({ to: '/' });

/** Render a `DataTable` and return the table it shows, to change its state. */
const renderDataTable = async () => {
  let table: AppReactTable<TestRow> | undefined;

  const TableHarness = () => {
    table = useAppTable(
      {
        data,
        columns,
        getRowCanExpand: () => true,
        initialState: { columnVisibility: { second: false } },
      },
      selectNoTableState
    );

    return (
      <DataTable
        table={table}
        renderSubComponent={renderSubComponent}
        setCurrentPageOptions={linkOptions}
        setPageSizeOptions={linkOptions}
      />
    );
  };

  const result = renderInteractiveWithRouter(<TableHarness />, { path: '/' });
  await screen.findByRole('button', { name: 'Toggle core' });

  if (!table) throw new Error('The table harness did not render.');

  return { ...result, table };
};

describe('DataTableBody', () => {
  it('shows and hides the sub-component when a row is expanded and collapsed', async () => {
    const { user } = await renderDataTable();

    expect(screen.queryByText('Details of core')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Toggle core' }));

    expect(screen.getByText('Details of core')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Toggle core' }));

    expect(screen.queryByText('Details of core')).not.toBeInTheDocument();
  });

  // Hiding one column and showing another in the same update keeps the number
  // of visible columns, so the props of the body stay the same; only the rows'
  // methods return the new cells.
  it('shows the cells of the columns that are visible', async () => {
    const { table } = await renderDataTable();

    expect(screen.getByRole('cell', { name: 'First' })).toBeInTheDocument();
    expect(
      screen.queryByRole('cell', { name: 'Second' })
    ).not.toBeInTheDocument();

    act(() => {
      table.setColumnVisibility({ first: false, second: true });
    });

    expect(
      screen.queryByRole('cell', { name: 'First' })
    ).not.toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'Second' })).toBeInTheDocument();
  });
});
