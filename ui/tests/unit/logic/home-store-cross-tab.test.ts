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

import type { RecentRunItemInput } from '@/providers/home-data/types';

/*
 * Simulate two browser tabs by loading the store module twice against one shared local
 * storage. Each load registers its own storage event listener, which is invoked for the
 * other tab only, just like a browser does.
 */

const localStorageContent = new Map<string, string>();
const storageEventListeners: Array<(event: Partial<StorageEvent>) => void> = [];

const localStorage = {
  getItem: (key: string) => localStorageContent.get(key) ?? null,
  setItem: (key: string, value: string) =>
    void localStorageContent.set(key, value),
  removeItem: (key: string) => void localStorageContent.delete(key),
} as unknown as Storage;

vi.stubGlobal('window', {
  localStorage,
  addEventListener: (
    type: string,
    listener: (event: Partial<StorageEvent>) => void
  ) => {
    if (type === 'storage') storageEventListeners.push(listener);
  },
});

const USER = 'user';
const STORAGE_NAME = 'home-recent-runs-storage';

const recentRun = (runId: number): RecentRunItemInput => ({
  id: `run:${runId}`,
  runId,
  runIndex: runId,
  organizationId: 2,
  organizationName: 'Acme',
  productId: 3,
  productName: 'Product',
  repositoryId: 4,
  repositoryName: 'Repository',
  to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex',
  params: {
    orgId: '2',
    productId: '3',
    repoId: '4',
    runIndex: runId.toString(),
  },
});

/** Load the store module again to get an instance that stands for another tab. */
const openTab = async () => {
  vi.resetModules();
  const { useRecentRunsStore } = await import('@/store/recent-runs.store');
  await useRecentRunsStore.persist.rehydrate();

  return {
    store: useRecentRunsStore,
    listenerIndex: storageEventListeners.length - 1,
    recordRun: (runId: number) =>
      useRecentRunsStore.getState().recordRecentRun(USER, recentRun(runId)),
    recordedRunIds: () =>
      (useRecentRunsStore.getState().recentRunsByUser[USER] ?? []).map(
        (run) => run.runId
      ),
  };
};

/** Deliver a storage event to the given tab, as a browser does for the other tabs. */
const notifyTab = (tab: { listenerIndex: number }) =>
  storageEventListeners[tab.listenerIndex]?.({
    key: STORAGE_NAME,
    storageArea: localStorage,
  });

const storedRunIds = () =>
  (
    JSON.parse(localStorageContent.get(STORAGE_NAME) ?? 'null')?.state
      ?.recentRunsByUser?.[USER] ?? []
  ).map((run: { runId: number }) => run.runId);

beforeEach(() => {
  localStorageContent.clear();
  storageEventListeners.length = 0;
});

it('keeps the runs of both tabs when each of them starts one', async () => {
  const firstTab = await openTab();
  const secondTab = await openTab();

  secondTab.recordRun(45);
  notifyTab(firstTab);
  firstTab.recordRun(46);

  expect(firstTab.recordedRunIds()).toStrictEqual([46, 45]);
  expect(storedRunIds()).toStrictEqual([46, 45]);
});

it('shows the run of another tab without starting one', async () => {
  const firstTab = await openTab();
  const secondTab = await openTab();

  secondTab.recordRun(45);
  notifyTab(firstTab);

  expect(firstTab.recordedRunIds()).toStrictEqual([45]);
});
