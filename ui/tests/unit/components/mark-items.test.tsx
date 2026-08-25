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
import { screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { MarkItems } from '@/components/data-table/mark-items';
import {
  selectNoTableState,
  useAppTable,
  type AppColumnDef,
} from '@/hooks/use-app-table';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

type TestRow = { id: string };

const columns: AppColumnDef<TestRow>[] = [{ accessorKey: 'id' }];
const data: TestRow[] = [{ id: 'row-1' }];

const setMarked = (marked: string): LinkOptions => ({
  search: { marked },
});

const MarkItemsHarness = () => {
  const table = useAppTable(
    {
      columns,
      data,
      getRowId: (row) => row.id,
    },
    selectNoTableState
  );

  return <MarkItems row={table.getRowModel().rows[0]!} setMarked={setMarked} />;
};

describe('MarkItems', () => {
  it('copies and navigates with a single link that shows a tooltip', async () => {
    const { container, router, user } = renderInteractiveWithRouter(
      <MarkItemsHarness />,
      {
        path: '/items',
        routes: [{ path: '/items' }],
      }
    );
    const writeText = vi.spyOn(navigator.clipboard, 'writeText');
    const link = await screen.findByRole('link');

    expect(link).toHaveAttribute('href', '/items?marked=row-1');
    expect(container.querySelector('a button')).not.toBeInTheDocument();

    link.focus();
    expect(
      await screen.findByText(
        'Copy the link to this item (ID: row-1) to clipboard.'
      )
    ).toBeVisible();

    await user.click(link);

    await waitFor(() => {
      expect(router.state.location.href).toBe('/items?marked=row-1');
      expect(writeText).toHaveBeenCalledOnce();
    });
  });
});
