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

import type { DependencyTreeRow } from './dependency-tree-model';

/** The most rows generated in one chunk. */
export const CHUNK_ROW_LIMIT = 1_000;

/** The time after which a chunk stops generating rows, in milliseconds. */
export const CHUNK_TIME_LIMIT_MS = 8;

/**
 * The number of rows after which generation pauses until the user asks for
 * more. Densely shared graphs can have more paths than fit into memory.
 */
export const ROW_LIMIT = 50_000;

/**
 * Runs `task` later and returns a function that cancels it. The default runs
 * the task as a separate browser task, so the browser can handle input and
 * draw the page between two chunks.
 */
export type ScheduleTask = (task: () => void) => () => void;

const scheduleWithTimeout: ScheduleTask = (task) => {
  const handle = setTimeout(task, 0);

  return () => clearTimeout(handle);
};

export type RowGenerationStatus =
  'running' | 'paused' | 'complete' | 'cancelled';

export type RowGenerationOptions = {
  /** Receives each chunk of generated rows, in order. */
  onRows: (rows: DependencyTreeRow[]) => void;
  onStatusChange?: (status: RowGenerationStatus) => void;
  chunkRowLimit?: number;
  chunkTimeLimitMs?: number;
  rowLimit?: number;
  scheduleTask?: ScheduleTask;
  /**
   * Generates the first chunk before returning instead of in a later task, so
   * that the caller can show the first rows without showing an empty tree.
   */
  firstChunkImmediately?: boolean;
  now?: () => number;
};

export type RowGeneration = {
  readonly status: RowGenerationStatus;
  /** The number of rows passed to `onRows` so far. */
  readonly rowCount: number;
  /** Continues a generation that paused at the row limit. */
  resume: () => void;
  /** Stops the generation; `onRows` is not called again afterwards. */
  cancel: () => void;
};

/**
 * Takes rows from `rows` in chunks, each in its own task, until there are no
 * more rows. Each chunk ends after `chunkRowLimit` rows or `chunkTimeLimitMs`,
 * whichever comes first, so that a large tree does not block the page.
 *
 * After `rowLimit` rows the generation pauses if more rows exist, and continues
 * for another `rowLimit` rows on `resume`. No rows are skipped or repeated.
 */
export const startRowGeneration = (
  rows: Iterator<DependencyTreeRow>,
  {
    onRows,
    onStatusChange,
    chunkRowLimit = CHUNK_ROW_LIMIT,
    chunkTimeLimitMs = CHUNK_TIME_LIMIT_MS,
    rowLimit = ROW_LIMIT,
    scheduleTask = scheduleWithTimeout,
    firstChunkImmediately = false,
    now = () => performance.now(),
  }: RowGenerationOptions
): RowGeneration => {
  let status: RowGenerationStatus = 'running';
  let rowCount = 0;
  let pauseAt = rowLimit;
  // The first row after the limit, taken to find out whether more rows exist.
  let nextRow: DependencyTreeRow | undefined;
  let cancelTask: (() => void) | undefined;

  const setStatus = (newStatus: RowGenerationStatus) => {
    status = newStatus;
    onStatusChange?.(newStatus);
  };

  const runChunk = () => {
    cancelTask = undefined;

    const deadline = now() + chunkTimeLimitMs;
    const chunk: DependencyTreeRow[] = [];
    let done = false;

    if (nextRow !== undefined) {
      chunk.push(nextRow);
      nextRow = undefined;
    }

    while (
      chunk.length < chunkRowLimit &&
      rowCount + chunk.length < pauseAt &&
      (chunk.length === 0 || now() < deadline)
    ) {
      const result = rows.next();

      if (result.done) {
        done = true;
        break;
      }

      chunk.push(result.value);
    }

    rowCount += chunk.length;
    if (chunk.length > 0) onRows(chunk);

    // `onRows` may have cancelled the generation.
    if (status !== 'running') return;

    if (done) {
      setStatus('complete');
      return;
    }

    if (rowCount >= pauseAt) {
      const result = rows.next();

      if (result.done) {
        setStatus('complete');
      } else {
        nextRow = result.value;
        setStatus('paused');
      }

      return;
    }

    cancelTask = scheduleTask(runChunk);
  };

  if (firstChunkImmediately) {
    runChunk();
  } else {
    cancelTask = scheduleTask(runChunk);
  }

  return {
    get status() {
      return status;
    },
    get rowCount() {
      return rowCount;
    },
    resume: () => {
      if (status !== 'paused') return;

      pauseAt = rowCount + rowLimit;
      setStatus('running');
      cancelTask = scheduleTask(runChunk);
    },
    cancel: () => {
      if (status === 'complete' || status === 'cancelled') return;

      cancelTask?.();
      cancelTask = undefined;
      nextRow = undefined;
      rows.return?.();
      setStatus('cancelled');
    },
  };
};
