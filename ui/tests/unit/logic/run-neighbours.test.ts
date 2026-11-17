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

import { beforeEach, describe, expect, it, vi } from 'vitest';

import { findRunNeighbours } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/-components/run-neighbours';

const mocks = vi.hoisted(() => ({
  getRepositoryRuns: vi.fn(),
}));

vi.mock('@/api/sdk.gen', () => mocks);

/** Serve the runs with the given indexes the way the API pages them, sorted by index. */
const serveRuns = (indexes: number[]) => {
  const sorted = [...indexes].sort((a, b) => a - b);

  mocks.getRepositoryRuns.mockImplementation(
    async ({ query }: { query: { limit: number; offset: number } }) => ({
      data: {
        data: sorted
          .slice(query.offset, query.offset + query.limit)
          .map((index) => ({ index })),
        pagination: {
          limit: query.limit,
          offset: query.offset,
          totalCount: sorted.length,
          sortProperties: [],
        },
      },
    })
  );
};

const neighboursOf = async (index: number) =>
  (await findRunNeighbours(1, index)).get(index);

const range = (from: number, to: number) =>
  Array.from({ length: to - from + 1 }, (_, i) => from + i);

describe('findRunNeighbours', () => {
  beforeEach(() => {
    mocks.getRepositoryRuns.mockReset();
  });

  it('finds the neighbours of a run older than the latest hundred', async () => {
    serveRuns(range(1, 274));

    expect(await neighboursOf(1)).toEqual({ previous: null, next: 2 });
    expect(await neighboursOf(150)).toEqual({
      previous: 149,
      next: 151,
    });
    expect(await neighboursOf(274)).toEqual({
      previous: 273,
      next: null,
    });
  });

  it('skips the gaps that deleted runs leave', async () => {
    serveRuns([1, 3, 5, ...range(8, 120)]);

    expect(await neighboursOf(3)).toEqual({ previous: 1, next: 5 });
    expect(await neighboursOf(8)).toEqual({ previous: 5, next: 9 });
  });

  it('asks for earlier pages when many earlier runs were deleted', async () => {
    // Runs 1 to 299 are gone except for every tenth one, so run 400 is far before position 399.
    serveRuns([
      ...range(1, 299).filter((i) => i % 10 === 0),
      ...range(300, 500),
    ]);

    expect(await neighboursOf(400)).toEqual({
      previous: 399,
      next: 401,
    });
    expect(await neighboursOf(300)).toEqual({
      previous: 290,
      next: 301,
    });
    expect(mocks.getRepositoryRuns.mock.calls.length).toBeGreaterThan(2);
  });

  it('skips the pages past the last run', async () => {
    serveRuns(range(9901, 10000));

    expect(await neighboursOf(10000)).toEqual({ previous: 9999, next: null });
    expect(mocks.getRepositoryRuns).toHaveBeenCalledTimes(2);
  });

  it('finds no neighbours for a run that does not exist', async () => {
    serveRuns([1, 2, 4, 5]);

    expect(await neighboursOf(3)).toBeUndefined();
    expect(await neighboursOf(9)).toBeUndefined();
  });

  it('returns the neighbours of the other runs on the page', async () => {
    serveRuns(range(1, 274));

    // The page of run 150 holds the runs 53 to 152, all on one request.
    const neighbours = await findRunNeighbours(1, 150);

    expect(mocks.getRepositoryRuns).toHaveBeenCalledOnce();
    expect(neighbours.get(149)).toEqual({ previous: 148, next: 150 });
    expect(neighbours.get(54)).toEqual({ previous: 53, next: 55 });
    // Moving to the next run finds its neighbours ready.
    expect(neighbours.get(151)).toEqual({ previous: 150, next: 152 });
    // The runs at the ends of the page have a neighbour on another page.
    expect(neighbours.has(53)).toBe(false);
    expect(neighbours.has(152)).toBe(false);
  });
});
