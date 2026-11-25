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

import { useSearch, type LinkOptions } from '@tanstack/react-router';
import { screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { DataTable } from '@/components/data-table/data-table';
import {
  createAppColumnHelper,
  selectNoTableState,
  useAppTable,
} from '@/hooks/use-app-table';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

type Row = { name: string };

const PAGE_SIZE = 10;

const columnHelper = createAppColumnHelper<Row>();

const columns = columnHelper.columns([
  columnHelper.accessor('name', { header: 'Name' }),
]);

const setCurrentPageOptions = (page: number): LinkOptions => ({
  search: { page },
});

const setPageSizeOptions = (pageSize: number): LinkOptions => ({
  search: { page: 1, pageSize },
});

/**
 * Render a server-paginated table of [totalCount] rows on the page from the URL, like the routes of the application
 * do. A page beyond the last one has no rows.
 */
const TableHarness = ({ totalCount }: { totalCount: number }) => {
  const search = useSearch({ strict: false }) as { page?: number };
  const page = search.page ?? 1;
  const firstIndex = (page - 1) * PAGE_SIZE;
  const rows = Array.from(
    { length: Math.max(0, Math.min(PAGE_SIZE, totalCount - firstIndex)) },
    (_, index) => ({ name: `row ${firstIndex + index + 1}` })
  );

  const table = useAppTable(
    {
      data: rows,
      columns,
      pageCount: Math.ceil(totalCount / PAGE_SIZE),
      state: { pagination: { pageIndex: page - 1, pageSize: PAGE_SIZE } },
    },
    selectNoTableState
  );

  return (
    <DataTable
      table={table}
      setCurrentPageOptions={setCurrentPageOptions}
      setPageSizeOptions={setPageSizeOptions}
    />
  );
};

const renderTable = (page: number, totalCount: number) =>
  renderInteractiveWithRouter(<TableHarness totalCount={totalCount} />, {
    path: `/items?page=${page}`,
    routes: [{ path: '/items' }],
  });

describe('DataTable', () => {
  it('navigates to the last page if the current page is beyond it', async () => {
    const { router } = renderTable(3, 15);

    await waitFor(() => {
      expect(router.state.location.search).toEqual({ page: 2 });
    });
    expect(await screen.findByText('row 15')).toBeInTheDocument();
    expect(
      screen.getByRole('link', { name: 'Go to last page' })
    ).toBeInTheDocument();
  });

  it('keeps the current page if it is not beyond the last page', async () => {
    const { router } = renderTable(2, 15);

    expect(await screen.findByText('row 11')).toBeInTheDocument();
    expect(router.state.location.search).toEqual({ page: 2 });
  });

  it('keeps the current page if there are no rows at all', async () => {
    const { router } = renderTable(3, 0);

    expect(await screen.findByText('No results.')).toBeInTheDocument();
    expect(router.state.location.search).toEqual({ page: 3 });
  });
});
