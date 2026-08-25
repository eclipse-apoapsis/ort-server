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
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { DataTablePagination } from '@/components/data-table/data-table-pagination';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

const setPageSizeOptions = (pageSize: number): LinkOptions => ({
  search: { page: 1, pageSize },
});

const setCurrentPageOptions = (page: number): LinkOptions => ({
  search: { page, pageSize: 10 },
});

const renderPagination = (currentPage: number) =>
  renderInteractiveWithRouter(
    <DataTablePagination
      currentPage={currentPage}
      totalPages={5}
      pageSize={10}
      setPageSizeOptions={setPageSizeOptions}
      setCurrentPageOptions={setCurrentPageOptions}
    />,
    {
      path: `/items?page=${currentPage}&pageSize=10`,
      routes: [{ path: '/items' }],
    }
  );

const getPageLinks = async () => ({
  first: await screen.findByRole('link', { name: 'Go to first page' }),
  previous: await screen.findByRole('link', { name: 'Go to previous page' }),
  next: await screen.findByRole('link', { name: 'Go to next page' }),
  last: await screen.findByRole('link', { name: 'Go to last page' }),
});

describe('DataTablePagination', () => {
  it('renders the four page controls as links without nested buttons', async () => {
    const { container } = renderPagination(2);
    const links = await getPageLinks();

    expect(Object.values(links)).toHaveLength(4);
    expect(container.querySelector('a button')).not.toBeInTheDocument();
  });

  it('does not activate disabled first and previous links', async () => {
    const { router, user } = renderPagination(1);
    const { first, previous, next } = await getPageLinks();
    const initialHref = router.state.location.href;
    const pageInput = screen.getByRole('spinbutton');

    await user.tab();
    await user.tab();
    await user.tab();
    expect(next).toHaveFocus();

    for (const link of [first, previous]) {
      expect(link).toHaveAttribute('aria-disabled', 'true');
      expect(link).toHaveAttribute('tabindex', '-1');

      await user.click(link);
      link.focus();
      await user.keyboard('{Enter}');
      fireEvent.click(link);
    }

    expect(router.state.location.href).toBe(initialHref);
    expect(pageInput).toHaveValue(1);
  });

  it('navigates and updates the page input from a middle page', async () => {
    const { router, user } = renderPagination(2);
    const { next } = await getPageLinks();

    expect(next).toHaveAttribute('href', '/items?page=3&pageSize=10');

    await user.click(next);

    await waitFor(() => {
      expect(router.state.location.href).toBe('/items?page=3&pageSize=10');
    });
    expect(screen.getByRole('spinbutton')).toHaveValue(3);
  });
});
