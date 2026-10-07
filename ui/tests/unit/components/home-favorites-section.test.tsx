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

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, type AxiosResponse } from 'axios';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { Organization } from '@/api';
import {
  getOrganization,
  getRepositoryRun,
  getServerSettingByKey,
  getSuperuser,
} from '@/api/sdk.gen';
import { TooltipProvider } from '@/components/ui/tooltip';
import {
  buildOrganizationFavorite,
  HomeDataProvider,
  type FavoriteType,
  type RecentRunItem,
} from '@/providers/home-data';
import { HomeFavoritesSection } from '@/routes/-components/home-favorites-section';
import { Route as HomeRoute } from '@/routes/index';
import { useFavoritesStore } from '@/store/favorites.store';
import { useRecentRunsStore } from '@/store/recent-runs.store';
import {
  compiledFunctionNames,
  isCompiledByReactCompiler,
} from '../fixtures/react-compiler';
import {
  createTestRouter,
  RouterTestProvider,
} from '../fixtures/router-harness';

const USER_ID = 'user-a';

vi.mock('react-oidc-context', () => ({
  useAuth: () => ({ user: { profile: { sub: 'user-a', name: 'Ada' } } }),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  getOrganization: vi.fn(),
  getRepositoryRun: vi.fn(),
  getServerSettingByKey: vi.fn(),
  getSuperuser: vi.fn(),
}));

const HomePage = HomeRoute.options.component as () => ReactNode;

const organization = (id: number, name: string) =>
  ({ id, name }) as Organization;

const notFound = () =>
  new AxiosError('Not Found', '404', undefined, undefined, {
    status: 404,
  } as AxiosResponse);

const runsRoute =
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex';

const renderHomePage = () => {
  const queryClient = new QueryClient({
    // The recent runs retry a failed request; do not wait between attempts.
    defaultOptions: { queries: { retry: false, retryDelay: 0 } },
  });
  const router = createTestRouter({
    path: '/',
    routes: [
      { path: '/', component: HomePage },
      { path: '/organizations' },
      { path: '/organizations/$orgId' },
      { path: runsRoute },
    ],
  });

  render(
    <QueryClientProvider client={queryClient}>
      <HomeDataProvider>
        <TooltipProvider>
          <RouterTestProvider router={router} />
        </TooltipProvider>
      </HomeDataProvider>
    </QueryClientProvider>
  );
};

const favoritesStore = () => useFavoritesStore.getState();

const addFavorite = (id: number, name: string) =>
  act(() =>
    favoritesStore().addFavorite(
      USER_ID,
      buildOrganizationFavorite(organization(id, name))
    )
  );

const favoritesCard = () =>
  screen.getByText('Favorites').closest('[data-slot="card"]') as HTMLElement;

const groupTitles = () =>
  within(favoritesCard())
    .getAllByRole('button', { name: /^Reorder / })
    .map((button) => button.getAttribute('aria-label'));

beforeEach(() => {
  vi.mocked(getServerSettingByKey).mockResolvedValue({
    data: { isEnabled: false, value: null },
  } as Awaited<ReturnType<typeof getServerSettingByKey>>);
  vi.mocked(getSuperuser).mockResolvedValue({
    data: false,
  } as Awaited<ReturnType<typeof getSuperuser>>);
  vi.mocked(getOrganization).mockImplementation((async (options: {
    path: { organizationId: number };
  }) => ({
    data: organization(options.path.organizationId, 'Example org'),
  })) as unknown as typeof getOrganization);
});

afterEach(() => {
  act(() => {
    useFavoritesStore.setState({
      favoritesByUser: {},
      favoriteGroupOrderByUser: {},
    });
    useRecentRunsStore.setState({ recentRunsByUser: {} });
  });
  localStorage.clear();
});

describe('home page', () => {
  it.each([
    ['HomePage', HomePage],
    ['HomeFavoritesSection', HomeFavoritesSection],
  ])('compiles %s with React Compiler', (_, component) => {
    expect(isCompiledByReactCompiler(component)).toBe(true);
  });

  it('compiles the components that are not exported', () => {
    expect(
      compiledFunctionNames('src/routes/-components/home-favorites-section.tsx')
    ).toEqual(
      expect.arrayContaining([
        'FavoriteListItem',
        'FavoriteGroupCard',
        'SortableFavoriteGroupCard',
      ])
    );
    expect(
      compiledFunctionNames(
        'src/routes/-components/home-recent-runs-section.tsx'
      )
    ).toContain('RecentRunListItem');
  });

  it('shows a favorite once one is added to the store', async () => {
    renderHomePage();

    expect(await screen.findByText(/Use the star button/)).toBeInTheDocument();

    addFavorite(1, 'Example org');

    expect(
      await within(favoritesCard()).findByRole('link', { name: 'Example org' })
    ).toBeInTheDocument();
  });

  it('removes a favorite with its star button', async () => {
    const user = userEvent.setup();
    addFavorite(1, 'Example org');
    renderHomePage();

    await user.click(
      await screen.findByRole('button', {
        name: 'Remove from favorites: Example org',
      })
    );

    expect(
      within(favoritesCard()).queryByRole('link', { name: 'Example org' })
    ).toBeNull();
    expect(favoritesStore().favoritesByUser[USER_ID]).toEqual([]);
  });

  it('removes a favorite whose organization no longer exists', async () => {
    vi.mocked(getOrganization).mockRejectedValue(notFound());
    addFavorite(9, 'Deleted org');
    renderHomePage();

    await waitFor(() =>
      expect(favoritesStore().favoritesByUser[USER_ID]).toEqual([])
    );
  });

  it('orders the favorite groups as stored', async () => {
    addFavorite(1, 'Example org');
    renderHomePage();

    expect(await screen.findByText('Example org')).toBeInTheDocument();
    expect(groupTitles()).toEqual([
      'Reorder Runs',
      'Reorder Repositories',
      'Reorder Products',
      'Reorder Organizations',
    ]);

    const order: FavoriteType[] = [
      'organization',
      'product',
      'repository',
      'run',
    ];
    act(() => favoritesStore().setFavoriteGroupOrder(USER_ID, order));

    expect(groupTitles()).toEqual([
      'Reorder Organizations',
      'Reorder Products',
      'Reorder Repositories',
      'Reorder Runs',
    ]);
  });

  it('marks a recent run that can no longer be loaded as unavailable', async () => {
    vi.mocked(getRepositoryRun).mockRejectedValue(notFound());
    const recentRun: RecentRunItem = {
      id: 'run-1',
      runId: 1,
      runIndex: 1,
      organizationId: 1,
      organizationName: 'Example org',
      productId: 2,
      productName: 'Example product',
      repositoryId: 3,
      repositoryName: 'Example repo',
      to: runsRoute,
      params: { orgId: '1', productId: '2', repoId: '3', runIndex: '1' },
      recordedAt: '2026-01-01T00:00:00Z',
    };
    act(() =>
      useRecentRunsStore.getState().recordRecentRun(USER_ID, recentRun)
    );
    renderHomePage();

    await waitFor(() =>
      expect(
        useRecentRunsStore.getState().recentRunsByUser[USER_ID]?.[0]
          ?.unavailable
      ).toBe(true)
    );
  });
});
