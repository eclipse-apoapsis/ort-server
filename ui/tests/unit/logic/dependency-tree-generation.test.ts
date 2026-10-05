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

import { afterEach, describe, expect, it, vi } from 'vitest';

import type { DependencyGraph } from '@/api';
import {
  buildAdjacencyMap,
  createNodeSubtreeMatcher,
} from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies/-components/dependency-graph-utils';
import {
  startRowGeneration,
  type RowGenerationOptions,
  type RowGenerationStatus,
} from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies/-components/dependency-tree-generation';
import {
  createDependencyTreeContext,
  generateRows,
  type DependencyTreeRow,
} from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/dependencies/-components/dependency-tree-model';
import { packageIdTypeSchema } from '@/schemas';
import {
  createDependencyGraph,
  createSharedDependencyGraph,
} from '../fixtures/dependency-graph';

const rowsOf = (graph: DependencyGraph, searchTerm: string) => {
  const adjacency = buildAdjacencyMap(graph);
  const context = createDependencyTreeContext(
    graph,
    adjacency,
    createNodeSubtreeMatcher(
      graph,
      adjacency,
      searchTerm,
      packageIdTypeSchema.enum.ORT_ID
    )
  );

  return generateRows(context, { searchTerm, overrides: new Map() });
};

/** Runs scheduled tasks only when the test asks for it. */
const createTaskQueue = () => {
  const tasks = new Set<() => void>();

  return {
    scheduleTask: (task: () => void) => {
      tasks.add(task);

      return () => tasks.delete(task);
    },
    get size() {
      return tasks.size;
    },
    runNext: () => {
      const [task] = tasks;
      if (task === undefined) return false;

      tasks.delete(task);
      task();

      return true;
    },
    runAll() {
      while (this.runNext());
    },
  };
};

const start = (
  rows: Iterator<DependencyTreeRow>,
  options: Partial<RowGenerationOptions> = {}
) => {
  const queue = createTaskQueue();
  const chunks: DependencyTreeRow[][] = [];
  const statuses: RowGenerationStatus[] = [];
  const generation = startRowGeneration(rows, {
    onRows: (chunk) => chunks.push(chunk),
    onStatusChange: (status) => statuses.push(status),
    scheduleTask: queue.scheduleTask,
    now: () => 0,
    ...options,
  });

  return { chunks, generation, queue, statuses };
};

describe('startRowGeneration', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('generates rows in chunks of at most the chunk row limit', () => {
    const graph = createSharedDependencyGraph(4, 4);
    const expected = [...rowsOf(graph, 'leaf')];
    const { chunks, generation, queue, statuses } = start(
      rowsOf(graph, 'leaf'),
      { chunkRowLimit: 50 }
    );

    expect(chunks).toHaveLength(0);

    queue.runNext();

    expect(chunks).toHaveLength(1);
    expect(chunks[0]).toHaveLength(50);

    queue.runAll();

    expect(chunks.every((chunk) => chunk.length <= 50)).toBe(true);
    expect(chunks.flat()).toEqual(expected);
    expect(generation.rowCount).toBe(expected.length);
    expect(generation.status).toBe('complete');
    expect(statuses).toEqual(['complete']);
  });

  it('ends a chunk when its time is up', () => {
    let time = 0;
    const { chunks, queue } = start(
      rowsOf(createDependencyGraph(), 'logback'),
      {
        chunkTimeLimitMs: 8,
        // The clock advances by 3 ms whenever it is read.
        now: () => (time += 3),
      }
    );

    queue.runNext();

    // The chunk starts at 3 ms and ends at 11 ms. The first row is always
    // taken, the clock is then read at 6, 9 and 12 ms.
    expect(chunks[0]).toHaveLength(3);
    expect(queue.size).toBe(1);
  });

  it('runs the chunks as separate tasks by default', async () => {
    vi.useFakeTimers();
    const chunks: DependencyTreeRow[][] = [];

    startRowGeneration(rowsOf(createDependencyGraph(), 'logback'), {
      onRows: (chunk) => chunks.push(chunk),
      chunkRowLimit: 5,
    });
    await Promise.resolve();

    expect(chunks).toHaveLength(0);

    vi.advanceTimersToNextTimer();

    expect(chunks).toHaveLength(1);

    vi.runAllTimers();

    expect(chunks.flat()).toHaveLength(15);
  });

  it('generates the first chunk before returning when asked to', () => {
    const { chunks, generation, queue } = start(
      rowsOf(createDependencyGraph(), 'logback'),
      { chunkRowLimit: 5, firstChunkImmediately: true }
    );

    expect(chunks).toHaveLength(1);
    expect(queue.size).toBe(1);

    generation.cancel();
    queue.runAll();

    expect(chunks).toHaveLength(1);
  });

  it('pauses at the row limit and resumes without missing or repeated rows', () => {
    const graph = createSharedDependencyGraph(4, 4);
    const expected = [...rowsOf(graph, 'leaf')];
    const { chunks, generation, queue, statuses } = start(
      rowsOf(graph, 'leaf'),
      { chunkRowLimit: 30, rowLimit: 100 }
    );

    queue.runAll();

    expect(generation.status).toBe('paused');
    expect(generation.rowCount).toBe(100);
    expect(queue.size).toBe(0);

    generation.resume();
    queue.runAll();

    expect(generation.rowCount).toBe(200);

    while (generation.status === 'paused') {
      generation.resume();
      queue.runAll();
    }

    expect(chunks.flat()).toEqual(expected);
    expect(generation.status).toBe('complete');
    expect(statuses.filter((status) => status === 'paused')).toHaveLength(3);
  });

  it('completes instead of pausing when the row limit is the last row', () => {
    const expected = [...rowsOf(createDependencyGraph(), 'logback')];
    const { generation, queue } = start(
      rowsOf(createDependencyGraph(), 'logback'),
      { rowLimit: expected.length }
    );

    queue.runAll();

    expect(generation.status).toBe('complete');
    expect(generation.rowCount).toBe(expected.length);
  });

  it('pauses a densely shared graph without enumerating all its paths', () => {
    // 4 ** 12 paths, far too many to enumerate.
    const { generation, queue } = start(
      rowsOf(createSharedDependencyGraph(12, 4), 'leaf'),
      { rowLimit: 2_000 }
    );

    queue.runAll();

    expect(generation.status).toBe('paused');
    expect(generation.rowCount).toBe(2_000);
  });

  it('passes no rows after it was cancelled', () => {
    const { chunks, generation, queue, statuses } = start(
      rowsOf(createDependencyGraph(), 'logback'),
      { chunkRowLimit: 5 }
    );

    queue.runNext();
    generation.cancel();
    queue.runAll();

    expect(chunks).toHaveLength(1);
    expect(generation.status).toBe('cancelled');
    expect(statuses).toEqual(['cancelled']);
  });

  it('passes only the rows of a new generation after cancelling the old one', () => {
    const queue = createTaskQueue();
    const received: string[] = [];
    const startSearch = (searchTerm: string) =>
      startRowGeneration(rowsOf(createDependencyGraph(), searchTerm), {
        onRows: (chunk) => received.push(...chunk.map(() => searchTerm)),
        chunkRowLimit: 2,
        scheduleTask: queue.scheduleTask,
        now: () => 0,
      });

    const first = startSearch('logback');
    queue.runNext();
    first.cancel();
    startSearch('json');
    queue.runAll();

    expect(received.slice(0, 2)).toEqual(['logback', 'logback']);
    expect(received.slice(2).every((term) => term === 'json')).toBe(true);
    expect(received.length).toBeGreaterThan(2);
  });

  it('can be cancelled while paused and while passing rows', () => {
    const paused = start(rowsOf(createSharedDependencyGraph(4, 4), 'leaf'), {
      rowLimit: 10,
    });

    paused.queue.runAll();
    paused.generation.cancel();
    paused.generation.resume();
    paused.queue.runAll();

    expect(paused.generation.status).toBe('cancelled');
    expect(paused.chunks.flat()).toHaveLength(10);

    const queue = createTaskQueue();
    const chunks: DependencyTreeRow[][] = [];
    const generation = startRowGeneration(
      rowsOf(createDependencyGraph(), 'logback'),
      {
        onRows: (chunk) => {
          chunks.push(chunk);
          generation.cancel();
        },
        chunkRowLimit: 5,
        scheduleTask: queue.scheduleTask,
        now: () => 0,
      }
    );

    queue.runAll();

    expect(chunks).toHaveLength(1);
    expect(generation.status).toBe('cancelled');
  });
});
