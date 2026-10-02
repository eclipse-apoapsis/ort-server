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

import { screen, waitFor } from '@testing-library/react';
import { Suspense } from 'react';
import {
  afterEach,
  beforeAll,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest';

import type { GetOrganizationsData, Organization } from '@/api';
import { getOrganizationsQueryKey } from '@/api/@tanstack/react-query.gen';
import { TooltipProvider } from '@/components/ui/tooltip';
import { OrganizationsPage } from '@/routes/organizations/index';
import { useTablePrefsStore } from '@/store/table-prefs.store';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

type OrganizationsQuery = NonNullable<GetOrganizationsData['query']>;

const TOTAL_COUNT = 25;

const mocks = vi.hoisted(() => ({
  organizations: vi.fn(),
  superuser: vi.fn(),
  namePrefix: 'org',
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  getOrganizations: mocks.organizations,
  getSuperuser: mocks.superuser,
}));

vi.mock('@/components/favorite-button', () => ({
  OrganizationFavoriteButton: () => null,
}));

// Serve a page of organizations named after their position, so that the rows tell which query
// they come from. A filter narrows the result to a single organization named after the filter.
const getOrganizationsPage = (query: OrganizationsQuery = {}) => {
  const limit = query.limit ?? TOTAL_COUNT;
  const offset = query.offset ?? 0;
  const names = query.filter
    ? [`${mocks.namePrefix}-${query.filter}`]
    : Array.from(
        { length: Math.min(limit, TOTAL_COUNT - offset) },
        (_, index) => `${mocks.namePrefix}-${offset + index + 1}`
      );
  const data: Organization[] = names.map((name, index) => ({
    id: offset + index + 1,
    name,
  }));

  return {
    data,
    pagination: {
      limit,
      offset,
      totalCount: query.filter ? data.length : TOTAL_COUNT,
    },
  };
};

// The queries issued for the table, without the query for the total number of organizations.
const tableQueries = () =>
  mocks.organizations.mock.calls
    .map(([options]) => options.query as OrganizationsQuery)
    .filter((query) => query.offset !== undefined);

const renderOrganizations = (search = '') =>
  renderInteractiveWithRouter(
    <Suspense>
      <TooltipProvider>
        <OrganizationsPage />
      </TooltipProvider>
    </Suspense>,
    {
      path: `/organizations/${search}`,
      routes: [{ path: '/organizations/' }],
      withQueryClient: true,
    }
  );

describe('organizations table', () => {
  beforeAll(() => {
    // Radix Select relies on DOM APIs that jsdom does not implement.
    Element.prototype.hasPointerCapture = () => false;
    Element.prototype.releasePointerCapture = () => {};
    Element.prototype.scrollIntoView = () => {};
  });

  beforeEach(() => {
    vi.clearAllMocks();
    mocks.namePrefix = 'org';
    mocks.organizations.mockImplementation(
      async ({ query }: { query?: OrganizationsQuery }) => ({
        data: getOrganizationsPage(query),
      })
    );
    mocks.superuser.mockResolvedValue({ data: true });
  });

  afterEach(() => {
    useTablePrefsStore.setState({ orgPageSize: 10 });
  });

  it('is compiled by React Compiler', () => {
    expect(OrganizationsPage.toString()).toContain('react.memo_cache_sentinel');
  });

  it('filters by name, starting from the first page', async () => {
    const { router, user } = renderOrganizations('?page=2');

    expect(await screen.findByRole('link', { name: 'org-11' })).toBeVisible();

    await user.click(screen.getByRole('button', { name: '' }));
    await user.type(
      await screen.findByPlaceholderText('(regular expression)'),
      'core{Enter}'
    );

    expect(await screen.findByRole('link', { name: 'org-core' })).toBeVisible();
    expect(
      screen.queryByRole('link', { name: 'org-11' })
    ).not.toBeInTheDocument();
    expect(router.state.location.search).toMatchObject({
      page: 1,
      filter: 'core',
    });
    expect(tableQueries().at(-1)).toEqual({
      limit: 10,
      offset: 0,
      filter: 'core',
    });
    expect(
      screen.getByText('Organizations (25 in total, 1 matching filters)')
    ).toBeVisible();
  });

  it('shows the next page', async () => {
    const { router, user } = renderOrganizations();

    expect(await screen.findByRole('link', { name: 'org-1' })).toBeVisible();

    await user.click(screen.getByRole('link', { name: 'Go to next page' }));

    expect(await screen.findByRole('link', { name: 'org-11' })).toBeVisible();
    expect(
      screen.queryByRole('link', { name: 'org-1' })
    ).not.toBeInTheDocument();
    expect(router.state.location.search).toMatchObject({ page: 2 });
    expect(tableQueries().at(-1)).toEqual({ limit: 10, offset: 10 });
    expect(screen.getByRole('spinbutton')).toHaveValue(2);
  });

  it('changes the page size, starting from the first page', async () => {
    const { router, user } = renderOrganizations('?page=2');

    expect(await screen.findByRole('link', { name: 'org-11' })).toBeVisible();

    await user.click(screen.getByRole('combobox'));
    await user.click(screen.getByRole('option', { name: '20' }));

    expect(await screen.findByRole('link', { name: 'org-1' })).toBeVisible();
    expect(screen.getByRole('link', { name: 'org-20' })).toBeVisible();
    expect(router.state.location.search).toMatchObject({
      page: 1,
      pageSize: 20,
    });
    expect(tableQueries().at(-1)).toEqual({ limit: 20, offset: 0 });
    expect(screen.getByRole('combobox')).toHaveTextContent('20');
    expect(screen.getByRole('spinbutton')).toHaveValue(1);
  });

  it('replaces the rows when the query data is refreshed', async () => {
    const { queryClient, router } = renderOrganizations();

    expect(await screen.findByRole('link', { name: 'org-1' })).toBeVisible();
    const initialHref = router.state.location.href;

    mocks.namePrefix = 'renamed';
    await queryClient!.invalidateQueries({
      queryKey: getOrganizationsQueryKey(),
    });

    await waitFor(() =>
      expect(screen.getByRole('link', { name: 'renamed-1' })).toBeVisible()
    );
    expect(
      screen.queryByRole('link', { name: 'org-1' })
    ).not.toBeInTheDocument();
    expect(router.state.location.href).toBe(initialHref);
  });
});
