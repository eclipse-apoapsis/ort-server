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

import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeAll, describe, expect, it, vi } from 'vitest';

import { DataTableCardsHeader } from '@/components/data-table-cards/data-table-cards-header';
import { DataTableFilter } from '@/components/data-table/data-table-filter';
import { DataTableHeader } from '@/components/data-table/data-table-header';
import { FilterInfiniteMultiSelect } from '@/components/data-table/filter-infinite-multi-select';
import { FilterMultiSelect } from '@/components/data-table/filter-multi-select';
import { FilterRegex } from '@/components/data-table/filter-regex';
import { FilterSingleSelect } from '@/components/data-table/filter-single-select';
import { FilterText } from '@/components/data-table/filter-text';
import {
  selectNoTableState,
  useAppTable,
  type AppColumnDef,
  type AppReactTable,
} from '@/hooks/use-app-table';
import { isCompiledByReactCompiler } from '../fixtures/react-compiler';

const openFilter = async (user: ReturnType<typeof userEvent.setup>) =>
  user.click(screen.getByRole('button'));

describe('table filters', () => {
  it.each([
    ['FilterText', FilterText],
    ['FilterRegex', FilterRegex],
    ['FilterSingleSelect', FilterSingleSelect],
    ['FilterMultiSelect', FilterMultiSelect],
    ['FilterInfiniteMultiSelect', FilterInfiniteMultiSelect],
  ])('compiles %s with React Compiler', (_, component) => {
    expect(isCompiledByReactCompiler(component)).toBe(true);
  });
});

describe('FilterText', () => {
  it('shows a filter value that changed from outside', async () => {
    const user = userEvent.setup();
    const { rerender } = render(
      <FilterText filterValue='first' setFilterValue={() => {}} />
    );

    rerender(<FilterText filterValue='second' setFilterValue={() => {}} />);
    await openFilter(user);

    expect(screen.getByRole('textbox')).toHaveValue('second');
  });

  it('passes the typed value to the latest handler', async () => {
    const user = userEvent.setup();
    const first = vi.fn();
    const second = vi.fn();
    const { rerender } = render(
      <FilterText filterValue='' setFilterValue={first} />
    );

    rerender(<FilterText filterValue='' setFilterValue={second} />);
    await openFilter(user);
    await user.type(screen.getByRole('textbox'), 'core{Enter}');

    expect(first).not.toHaveBeenCalled();
    expect(second).toHaveBeenCalledWith('core');
  });
});

describe('FilterRegex', () => {
  it('passes the typed expression to the latest handler', async () => {
    const user = userEvent.setup();
    const first = vi.fn();
    const second = vi.fn();
    const { rerender } = render(
      <FilterRegex filterValue='' setFilterValue={first} />
    );

    rerender(<FilterRegex filterValue='' setFilterValue={second} />);
    await openFilter(user);
    await user.type(screen.getByRole('textbox'), '^core{Enter}');

    expect(first).not.toHaveBeenCalled();
    expect(second).toHaveBeenCalledWith('^core');
  });
});

const optionsOf = (...labels: string[]) =>
  labels.map((label) => ({ label, value: label.toLowerCase() }));

describe('FilterSingleSelect', () => {
  it('offers changed options and passes a choice to the latest handler', async () => {
    const user = userEvent.setup();
    const first = vi.fn();
    const second = vi.fn();
    const { rerender } = render(
      <FilterSingleSelect
        title='Status'
        options={optionsOf('Open')}
        setSelected={first}
      />
    );

    rerender(
      <FilterSingleSelect
        title='Status'
        options={optionsOf('Open', 'Closed')}
        setSelected={second}
      />
    );
    await openFilter(user);
    await user.click(screen.getByRole('radio', { name: 'Closed' }));

    expect(first).not.toHaveBeenCalled();
    expect(second).toHaveBeenCalledWith('closed');
  });
});

describe('FilterMultiSelect', () => {
  beforeAll(() => {
    // cmdk scrolls the selected option into view, which jsdom does not implement.
    Element.prototype.scrollIntoView = () => {};
  });

  it('offers changed options and passes a choice to the latest handler', async () => {
    const user = userEvent.setup();
    const first = vi.fn();
    const second = vi.fn();
    const { rerender } = render(
      <FilterMultiSelect
        options={optionsOf('Open')}
        selected={['open']}
        setSelected={first}
      />
    );

    rerender(
      <FilterMultiSelect
        options={optionsOf('Open', 'Closed')}
        selected={['open']}
        setSelected={second}
      />
    );
    await openFilter(user);
    await user.click(screen.getByRole('option', { name: 'Closed' }));

    expect(first).not.toHaveBeenCalled();
    expect(second).toHaveBeenCalledWith(['open', 'closed']);
  });
});

type FilterTestRow = { name: string; note: string };

const textFilter = {
  filterVariant: 'text',
  setFilterValue: () => {},
} as const;

const filterColumns: AppColumnDef<FilterTestRow>[] = [
  { accessorKey: 'name', header: 'Name', meta: { filter: textFilter } },
  { accessorKey: 'note', header: 'Note', meta: { filter: textFilter } },
];

/**
 * Render the table header, which shows the filters of the visible columns, and
 * the card header, which shows the filters of the hidden ones. Return the
 * table, to change its filters.
 */
const renderFilterHeaders = () => {
  let table: AppReactTable<FilterTestRow> | undefined;

  const HeadersHarness = () => {
    table = useAppTable(
      {
        columns: filterColumns,
        data: [{ name: 'core', note: 'library' }],
        initialState: {
          columnVisibility: { note: false },
          columnFilters: [
            { id: 'name', value: 'first name' },
            { id: 'note', value: 'first note' },
          ],
        },
      },
      selectNoTableState
    );

    return (
      <>
        <table>
          <DataTableHeader table={table} />
        </table>
        <DataTableCardsHeader table={table} />
      </>
    );
  };

  render(<HeadersHarness />);

  if (!table) throw new Error('The headers harness did not render.');

  return table;
};

describe('DataTableFilter', () => {
  it('is not compiled with React Compiler', () => {
    expect(isCompiledByReactCompiler(DataTableFilter)).toBe(false);
  });

  it('shows a filter value that changed in the table', async () => {
    const user = userEvent.setup();
    const table = renderFilterHeaders();

    act(() => {
      table.setColumnFilters([
        { id: 'name', value: 'second name' },
        { id: 'note', value: 'second note' },
      ]);
    });

    await user.click(within(screen.getByRole('table')).getByRole('button'));
    expect(screen.getByRole('textbox')).toHaveValue('second name');

    await user.keyboard('{Escape}');
    await user.click(screen.getByRole('button', { name: 'Note' }));
    expect(screen.getByRole('textbox')).toHaveValue('second note');
  });
});
