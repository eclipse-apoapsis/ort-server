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

import type { RecentRunItem, RecentRunItemInput } from './types';

export const DEFAULT_MAX_RECENT_RUNS = 10;

export const buildRecentRunId = (runId: number | string) =>
  `run:${runId.toString()}`;

/** Create a stored recent run item and assign a timestamp if none is provided. */
export const createRecentRunItem = (
  recentRun: RecentRunItemInput,
  now = new Date()
): RecentRunItem => ({
  ...recentRun,
  recordedAt: recentRun.recordedAt ?? now.toISOString(),
});

/**
 * Add a run to the front of the recent-runs list.
 *
 * If the run is already in the list, replace the old entry and keep only the
 * configured number of entries.
 */
export const addRecentRunItem = (
  recentRuns: RecentRunItem[],
  recentRun: RecentRunItemInput,
  maxItems = DEFAULT_MAX_RECENT_RUNS,
  now = new Date()
): RecentRunItem[] => {
  const item = createRecentRunItem(recentRun, now);

  return [
    item,
    ...recentRuns.filter((existing) => existing.id !== item.id),
  ].slice(0, maxItems);
};

export const removeRecentRunItem = (
  recentRuns: RecentRunItem[],
  recentRunId: string
): RecentRunItem[] =>
  recentRuns.filter((recentRun) => recentRun.id !== recentRunId);

export const clearRecentRunItems = (): RecentRunItem[] => [];

if (import.meta.vitest) {
  const { describe, expect, it } = import.meta.vitest;

  describe('recent run helpers', () => {
    const createInput = (runId: number): RecentRunItemInput => ({
      id: buildRecentRunId(runId),
      runId,
      runIndex: runId,
      organizationId: 1,
      organizationName: 'Acme',
      productId: 2,
      productName: 'Product',
      repositoryId: 3,
      repositoryName: 'Repository',
      revision: `rev-${runId}`,
      status: 'CREATED',
      to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex',
      params: {
        orgId: '1',
        productId: '2',
        repoId: '3',
        runIndex: runId.toString(),
      },
      createdAt: `2026-06-16T12:00:0${runId}.000Z`,
    });

    it('builds stable recent run IDs', () => {
      expect(buildRecentRunId(42)).toBe('run:42');
    });

    it('adds recent runs newest first', () => {
      const first = addRecentRunItem(
        [],
        createInput(1),
        10,
        new Date('2026-06-16T12:00:01.000Z')
      );
      const second = addRecentRunItem(
        first,
        createInput(2),
        10,
        new Date('2026-06-16T12:00:02.000Z')
      );

      expect(second.map((run) => run.runId)).toStrictEqual([2, 1]);
    });

    it('de-duplicates recent runs by ID', () => {
      const first = addRecentRunItem(
        [],
        createInput(1),
        10,
        new Date('2026-06-16T12:00:01.000Z')
      );
      const updated = addRecentRunItem(
        first,
        { ...createInput(1), revision: 'updated' },
        10,
        new Date('2026-06-16T12:00:02.000Z')
      );

      expect(updated).toHaveLength(1);
      expect(updated[0]).toMatchObject({ runId: 1, revision: 'updated' });
      expect(updated[0]?.recordedAt).toBe('2026-06-16T12:00:02.000Z');
    });

    it('limits the number of recent runs', () => {
      const runs = [1, 2, 3].reduce<RecentRunItem[]>(
        (recentRuns, runId) =>
          addRecentRunItem(
            recentRuns,
            createInput(runId),
            2,
            new Date(`2026-06-16T12:00:0${runId}.000Z`)
          ),
        []
      );

      expect(runs.map((run) => run.runId)).toStrictEqual([3, 2]);
    });

    it('removes a recent run by ID', () => {
      const runs = [1, 2, 3].reduce<RecentRunItem[]>(
        (recentRuns, runId) =>
          addRecentRunItem(
            recentRuns,
            createInput(runId),
            10,
            new Date(`2026-06-16T12:00:0${runId}.000Z`)
          ),
        []
      );

      expect(removeRecentRunItem(runs, buildRecentRunId(2))).toStrictEqual([
        runs[0],
        runs[2],
      ]);
    });

    it('clears recent runs', () => {
      expect(clearRecentRunItems()).toStrictEqual([]);
    });
  });
}
