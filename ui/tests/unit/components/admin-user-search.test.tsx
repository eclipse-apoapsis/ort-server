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

import { screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { PagedResponseUserWithSuperuserStatus } from '@/api';
import { Route } from '@/routes/admin/users';
import { adminUsersSearchParameterSchema } from '@/schemas';
import { getQueryKeyRequest } from '../fixtures/loader-test-utils';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

const mocks = vi.hoisted(() => ({
  invalidateQueries: vi.fn(),
  queryOptions: vi.fn(),
  response: {
    data: [],
    pagination: {
      limit: 10,
      offset: 0,
      totalCount: 0,
      sortProperties: [],
    },
  } as PagedResponseUserWithSuperuserStatus,
}));

vi.mock('@tanstack/react-query', async (importOriginal) => {
  const original =
    await importOriginal<typeof import('@tanstack/react-query')>();

  return {
    ...original,
    useMutation: () => ({ mutateAsync: vi.fn() }),
    useQueryClient: () => ({ invalidateQueries: mocks.invalidateQueries }),
    useSuspenseQuery: (options: unknown) => {
      mocks.queryOptions(options);
      return { data: mocks.response };
    },
  };
});

const UsersComponent = Route.options.component!;

const createUser = (username: string) => ({
  isSuperuser: false,
  user: {
    username,
    firstName: `${username} first`,
    lastName: `${username} last`,
    email: `${username}@example.org`,
  },
});

const renderUsers = (path = '/admin/users') =>
  renderInteractiveWithRouter(<UsersComponent />, {
    path,
    routes: [{ path: '/admin/users/' }],
  });

const getLastUsersRequest = () =>
  getQueryKeyRequest(mocks.queryOptions.mock.calls.at(-1)![0]);

beforeEach(() => {
  mocks.queryOptions.mockClear();
  mocks.invalidateQueries.mockClear();
  mocks.response = {
    data: [createUser('zulu'), createUser('alpha'), createUser('mike')],
    pagination: {
      limit: 10,
      offset: 0,
      totalCount: 30,
      sortProperties: [],
    },
  };
});

describe('admin user list', () => {
  it('renders the search input between the create action and table', async () => {
    renderUsers();

    const createLink = await screen.findByRole('link', { name: /Create user/ });
    const searchInput = screen.getByRole('searchbox', { name: 'Search users' });
    const table = screen.getByRole('table');

    expect(
      createLink.compareDocumentPosition(searchInput) &
        Node.DOCUMENT_POSITION_FOLLOWING
    ).not.toBe(0);
    expect(
      searchInput.compareDocumentPosition(table) &
        Node.DOCUMENT_POSITION_FOLLOWING
    ).not.toBe(0);
  });

  it('applies search on Enter without applying it again on blur', async () => {
    const { router, user } = renderUsers('/admin/users?page=3&pageSize=10');
    const searchInput = await screen.findByRole('searchbox', {
      name: 'Search users',
    });
    const initialQueryCount = mocks.queryOptions.mock.calls.length;

    await user.type(searchInput, '  Example  ');

    expect(router.state.location.search).toMatchObject({
      page: 3,
      pageSize: 10,
    });
    mocks.queryOptions.mock.calls.forEach(([options]) => {
      expect(getQueryKeyRequest(options).query?.search).toBeUndefined();
    });
    expect(mocks.queryOptions).toHaveBeenCalledTimes(initialQueryCount);

    await user.keyboard('{Enter}');

    await waitFor(() => {
      expect(router.state.location.search).toMatchObject({
        page: 1,
        pageSize: 10,
        search: 'Example',
      });
    });
    expect(getLastUsersRequest().query).toMatchObject({
      limit: 10,
      offset: 0,
      search: 'Example',
    });

    const queryCountAfterEnter = mocks.queryOptions.mock.calls.length;
    await user.tab();
    await Promise.resolve();

    expect(mocks.queryOptions).toHaveBeenCalledTimes(queryCountAfterEnter);
  });

  it('applies search on blur, clears it, and follows browser navigation', async () => {
    const { router, user } = renderUsers(
      '/admin/users?page=2&pageSize=10&search=initial'
    );
    const searchInput = await screen.findByRole('searchbox', {
      name: 'Search users',
    });

    expect(searchInput).toHaveValue('initial');

    await user.clear(searchInput);
    await user.type(searchInput, '  next  ');
    await user.tab();

    await waitFor(() => {
      expect(router.state.location.search).toMatchObject({
        page: 1,
        search: 'next',
      });
    });

    const updatedSearchInput = screen.getByRole('searchbox', {
      name: 'Search users',
    });
    expect(updatedSearchInput).toHaveValue('next');

    const queryCountBeforeClear = mocks.queryOptions.mock.calls.length;
    await user.click(screen.getByRole('button', { name: 'Clear search' }));

    await waitFor(() => {
      expect(router.state.location.search.search).toBeUndefined();
    });
    mocks.queryOptions.mock.calls
      .slice(queryCountBeforeClear)
      .forEach(([options]) => {
        expect(getQueryKeyRequest(options).query?.search).toBeUndefined();
      });

    router.history.back();

    await waitFor(() => {
      expect(
        screen.getByRole('searchbox', { name: 'Search users' })
      ).toHaveValue('next');
    });
  });

  it('uses the server page without slicing or reordering it', async () => {
    renderUsers('/admin/users?page=2&pageSize=2');

    await screen.findByRole('table');

    expect(
      screen
        .getAllByRole('row')
        .slice(1)
        .map((row) => within(row).getAllByRole('cell')[0]?.textContent)
    ).toEqual(['zulu', 'alpha', 'mike']);
    expect(getLastUsersRequest().query).toMatchObject({
      limit: 2,
      offset: 2,
    });
    expect(screen.getByRole('spinbutton')).toHaveValue(2);
    expect(
      screen.getByRole('link', { name: 'Go to last page' })
    ).toHaveAttribute('href', expect.stringContaining('page=15'));
  });

  it('requests sorting while preserving the server row order', async () => {
    const { router, user } = renderUsers('/admin/users?page=2&pageSize=10');
    const usernameHeader = (await screen.findByText('Username')).closest('th');

    expect(usernameHeader).not.toBeNull();
    await user.click(within(usernameHeader!).getByRole('link'));

    await waitFor(() => {
      expect(router.state.location.search).toMatchObject({
        page: 1,
        sortBy: [{ id: 'username', desc: false }],
      });
    });
    expect(getLastUsersRequest().query?.sort).toBe('username');
    expect(
      screen
        .getAllByRole('row')
        .slice(1)
        .map((row) => within(row).getAllByRole('cell')[0]?.textContent)
    ).toEqual(['zulu', 'alpha', 'mike']);
  });

  it('rejects unsupported sort fields in URL search parameters', () => {
    expect(
      adminUsersSearchParameterSchema.safeParse({
        sortBy: [{ id: 'isSuperuser', desc: false }],
      }).success
    ).toBe(false);
  });
});
