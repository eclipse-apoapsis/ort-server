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

import { expect, it } from 'vitest';

import {
  addRecentRunItem,
  buildRecentRunId,
  clearRecentRunItems,
  removeRecentRunItem,
  setRecentRunItemUnavailable,
} from '@/providers/home-data/recent-runs';
import type {
  RecentRunItem,
  RecentRunItemInput,
} from '@/providers/home-data/types';

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

const seedRecentRuns = (runIds: number[], limit: number): RecentRunItem[] =>
  runIds.reduce<RecentRunItem[]>(
    (recentRuns, runId) =>
      addRecentRunItem(
        recentRuns,
        createInput(runId),
        limit,
        new Date(`2026-06-16T12:00:0${runId}.000Z`)
      ),
    []
  );

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

it('builds stable recent run IDs', () => {
  expect(buildRecentRunId(42)).toBe('run:42');
});

it('clears recent runs', () => {
  expect(clearRecentRunItems()).toStrictEqual([]);
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
  const runs = seedRecentRuns([1, 2, 3], 2);

  expect(runs.map((run) => run.runId)).toStrictEqual([3, 2]);
});

it('removes a recent run by ID', () => {
  const runs = seedRecentRuns([1, 2, 3], 10);

  expect(removeRecentRunItem(runs, buildRecentRunId(2))).toStrictEqual([
    runs[0],
    runs[2],
  ]);
});

const markUnavailable = (recentRuns: RecentRunItem[], runIds: number[]) =>
  runIds.reduce(
    (runs, runId) =>
      setRecentRunItemUnavailable(runs, buildRecentRunId(runId), true),
    recentRuns
  );

it('marks a recent run as unavailable and leaves the others alone', () => {
  const runs = seedRecentRuns([1, 2, 3], 10);
  const marked = markUnavailable(runs, [2]);

  expect(marked.map((run) => run.unavailable)).toStrictEqual([
    undefined,
    true,
    undefined,
  ]);
});

it('clears the mark once a run can be loaded again', () => {
  const marked = markUnavailable(seedRecentRuns([1], 10), [1]);
  const cleared = setRecentRunItemUnavailable(
    marked,
    buildRecentRunId(1),
    false
  );

  expect(cleared[0]?.unavailable).toBe(false);
});

it('keeps the list as it is when the mark does not change', () => {
  const runs = markUnavailable(seedRecentRuns([1, 2], 10), [1]);

  expect(setRecentRunItemUnavailable(runs, buildRecentRunId(1), true)).toBe(
    runs
  );
  expect(setRecentRunItemUnavailable(runs, buildRecentRunId(2), false)).toBe(
    runs
  );
});

it('keeps the list as it is for an unknown run', () => {
  const runs = seedRecentRuns([1, 2], 10);

  expect(setRecentRunItemUnavailable(runs, buildRecentRunId(3), true)).toBe(
    runs
  );
});

it('records a run again without its earlier mark', () => {
  const marked = markUnavailable(seedRecentRuns([1, 2], 10), [1]);
  const runs = addRecentRunItem(marked, createInput(1), 10);

  expect(runs[0]?.runId).toBe(1);
  expect(runs[0]?.unavailable).toBeUndefined();
});

it('evicts unavailable runs before runs that are still there', () => {
  const runs = markUnavailable(seedRecentRuns([1, 2, 3], 3), [2]);
  const withNewRun = addRecentRunItem(runs, createInput(4), 3);

  expect(withNewRun.map((run) => run.runId)).toStrictEqual([4, 3, 1]);
});

it('evicts the oldest unavailable runs first', () => {
  const runs = markUnavailable(seedRecentRuns([1, 2, 3, 4], 4), [1, 3]);
  const withNewRun = addRecentRunItem(runs, createInput(5), 4);

  expect(withNewRun.map((run) => run.runId)).toStrictEqual([5, 4, 3, 2]);
});

it('falls back to evicting the oldest run when no unavailable ones are left', () => {
  const runs = markUnavailable(seedRecentRuns([1, 2, 3], 3), [3]);
  const withNewRuns = [4, 5].reduce(
    (recentRuns, runId) => addRecentRunItem(recentRuns, createInput(runId), 3),
    runs
  );

  expect(withNewRuns.map((run) => run.runId)).toStrictEqual([5, 4, 2]);
});
