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

import { beforeEach, expect, it, vi } from 'vitest';

/*
 * Rehydrate the persisted stores against a stubbed local storage to cover what the pure
 * migrations cannot show on their own: that stored data of the current version is left
 * exactly as it is, and that data of another version survives instead of being dropped.
 */

const localStorageContent = new Map<string, string>();

vi.stubGlobal('window', {
  localStorage: {
    getItem: (key: string) => localStorageContent.get(key) ?? null,
    setItem: (key: string, value: string) =>
      void localStorageContent.set(key, value),
    removeItem: (key: string) => void localStorageContent.delete(key),
  },
  // The stores listen for the storage events of the other tabs while loading.
  addEventListener: () => {},
});

const CURRENT_VERSION = 2;

const recentRun = {
  id: 'run:1',
  runId: 1,
  runIndex: 11,
  organizationId: 2,
  organizationName: 'Acme',
  productId: 3,
  productName: 'Product',
  repositoryId: 4,
  repositoryName: 'Repository',
  to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex',
  params: { orgId: '2', productId: '3', repoId: '4', runIndex: '11' },
  createdAt: '2026-06-16T12:00:00.000Z',
  recordedAt: '2026-06-16T12:00:01.000Z',
};

const favorite = {
  id: 'product:2:3',
  type: 'product',
  name: 'Product',
  breadcrumbs: ['Acme', 'Product'],
  to: '/organizations/$orgId/products/$productId',
  params: { orgId: '2', productId: '3' },
  starredAt: '2026-06-16T12:00:00.000Z',
};

const seed = (name: string, state: unknown, version: number) =>
  localStorageContent.set(name, JSON.stringify({ state, version }));

const storedState = (name: string) =>
  JSON.parse(localStorageContent.get(name) ?? 'null');

const rehydrateRecentRuns = async () => {
  const { useRecentRunsStore } = await import('@/store/recent-runs.store');
  await useRecentRunsStore.persist.rehydrate();

  return useRecentRunsStore.getState();
};

const rehydrateFavorites = async () => {
  const { useFavoritesStore } = await import('@/store/favorites.store');
  await useFavoritesStore.persist.rehydrate();

  return useFavoritesStore.getState();
};

beforeEach(() => {
  localStorageContent.clear();
  vi.resetModules();
});

it('leaves recent runs of the current version untouched', async () => {
  const state = { recentRunsByUser: { user: [recentRun] } };
  seed('home-recent-runs-storage', state, CURRENT_VERSION);

  expect((await rehydrateRecentRuns()).recentRunsByUser.user).toStrictEqual([
    recentRun,
  ]);
  expect(storedState('home-recent-runs-storage')).toStrictEqual({
    state,
    version: CURRENT_VERSION,
  });
});

it('keeps recent runs stored by another version', async () => {
  seed(
    'home-recent-runs-storage',
    { recentRunsByUser: { user: [recentRun] } },
    CURRENT_VERSION - 1
  );

  expect((await rehydrateRecentRuns()).recentRunsByUser.user).toStrictEqual([
    recentRun,
  ]);
  expect(storedState('home-recent-runs-storage').version).toBe(CURRENT_VERSION);
});

it('leaves favorites of the current version untouched', async () => {
  const state = {
    favoritesByUser: { user: [favorite] },
    favoriteGroupOrderByUser: {},
  };
  seed('home-favorites-storage', state, CURRENT_VERSION);

  expect((await rehydrateFavorites()).favoritesByUser.user).toStrictEqual([
    favorite,
  ]);
  expect(storedState('home-favorites-storage')).toStrictEqual({
    state,
    version: CURRENT_VERSION,
  });
});

it('keeps favorites stored by another version', async () => {
  seed(
    'home-favorites-storage',
    { favoritesByUser: { user: [favorite] }, favoriteGroupOrderByUser: {} },
    CURRENT_VERSION - 1
  );

  expect((await rehydrateFavorites()).favoritesByUser.user).toStrictEqual([
    favorite,
  ]);
  expect(storedState('home-favorites-storage').version).toBe(CURRENT_VERSION);
});
