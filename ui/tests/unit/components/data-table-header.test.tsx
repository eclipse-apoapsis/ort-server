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
import { screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { DataTableHeader } from '@/components/data-table/data-table-header';
import {
  selectNoTableState,
  useAppTable,
  type AppColumnDef,
} from '@/hooks/use-app-table';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

type TestRow = { name: string };

const columns: AppColumnDef<TestRow>[] = [
  {
    accessorKey: 'name',
    header: 'Name',
  },
];
const data: TestRow[] = [{ name: 'first' }];

const setSortingOptions = ({
  id,
  desc,
}: {
  id: string;
  desc: boolean | undefined;
}): LinkOptions => ({
  search: { sortBy: [{ id, desc: desc ?? false }] },
});

const HeaderHarness = ({ toggleSorting }: { toggleSorting: () => void }) => {
  const table = useAppTable(
    {
      columns,
      data,
      state: { sorting: [] },
    },
    selectNoTableState
  );
  const column = table.getColumn('name')!;
  column.getToggleSortingHandler = () => toggleSorting;

  return (
    <table>
      <DataTableHeader table={table} setSortingOptions={setSortingOptions} />
    </table>
  );
};

describe('DataTableHeader', () => {
  it('sorts through a link and shows its tooltip', async () => {
    const toggleSorting = vi.fn();
    const { container, user } = renderInteractiveWithRouter(
      <HeaderHarness toggleSorting={toggleSorting} />,
      {
        path: '/items',
        routes: [{ path: '/items' }],
      }
    );
    const link = await screen.findByRole('link');

    expect(link).toHaveAttribute(
      'href',
      '/items?sortBy=%5B%7B%22id%22%3A%22name%22%2C%22desc%22%3Afalse%7D%5D'
    );
    expect(container.querySelector('a button')).not.toBeInTheDocument();

    await user.hover(link);
    expect(await screen.findByText('Sort ascending')).toBeVisible();

    await user.click(link);
    expect(toggleSorting).toHaveBeenCalledOnce();
  });
});
