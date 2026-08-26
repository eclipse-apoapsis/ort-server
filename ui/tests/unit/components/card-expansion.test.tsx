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

import type { ExpandedState, SortingState } from '@tanstack/react-table';
import { screen } from '@testing-library/react';
import { useState } from 'react';
import { expect, it } from 'vitest';

import { DataTableCards } from '@/components/data-table-cards/data-table-cards';
import { Button } from '@/components/ui/button';
import { EMPTY_SORTING_STATE } from '@/helpers/handle-multisort';
import {
  selectNoTableState,
  useAppTable,
  type AppColumnDef,
} from '@/hooks/use-app-table';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

type TestRow = {
  category: string;
  name: string;
};

const data: TestRow[] = [
  { category: 'Example category', name: 'Example project' },
];
const CardTableHarness = ({ sortBy }: { sortBy?: SortingState }) => {
  const [expanded, setExpanded] = useState<ExpandedState>({});
  const columns: AppColumnDef<TestRow>[] = [
    {
      id: 'details',
      cell: ({ row }) => (
        <Button
          aria-label={row.getIsExpanded() ? 'Collapse row' : 'Expand row'}
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
  const table = useAppTable(
    {
      columns,
      data,
      pageCount: 1,
      state: {
        columnFilters: [],
        columnVisibility: { category: false, name: false },
        expanded,
        pagination: { pageIndex: 0, pageSize: 10 },
        sorting: sortBy ?? EMPTY_SORTING_STATE,
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
    <DataTableCards
      table={table}
      renderSubComponent={({ row }) => <div>{row.original.name} details</div>}
      setCurrentPageOptions={() => ({ to: '/' })}
      setPageSizeOptions={() => ({ to: '/' })}
      setSortingOptions={() => ({ to: '/' })}
    />
  );
};

it('expands a card when sorting defaults to an empty state', async () => {
  const { user } = renderInteractiveWithRouter(<CardTableHarness />, {
    path: '/cards',
    routes: [{ path: '/cards' }],
  });

  await user.click(await screen.findByRole('button', { name: 'Expand row' }));

  expect(await screen.findByText('Example project details')).toBeVisible();
}, 1000);
