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
 * Trim the list to the configured number of entries.
 *
 * Runs that can no longer be loaded go first, oldest ones before newer ones, so that they
 * do not take the place of runs that are still there. This is the only case in which such
 * a run disappears without the user removing it.
 */
const pruneRecentRunItems = (
  recentRuns: RecentRunItem[],
  maxItems: number
): RecentRunItem[] => {
  if (recentRuns.length <= maxItems) return recentRuns;

  const excessItems = recentRuns.length - maxItems;
  const evictedItems = new Set(
    recentRuns
      .filter((recentRun) => recentRun.unavailable)
      .slice(-excessItems)
      .map((recentRun) => recentRun.id)
  );

  return recentRuns
    .filter((recentRun) => !evictedItems.has(recentRun.id))
    .slice(0, maxItems);
};

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

  return pruneRecentRunItems(
    [item, ...recentRuns.filter((existing) => existing.id !== item.id)],
    maxItems
  );
};

/**
 * Mark a run whose details can no longer be loaded, or clear that mark once they can be
 * loaded again.
 *
 * The list is returned unchanged when the run is already in the wanted state, so that
 * consumers do not re-render for nothing.
 */
export const setRecentRunItemUnavailable = (
  recentRuns: RecentRunItem[],
  recentRunId: string,
  unavailable: boolean
): RecentRunItem[] => {
  const recentRun = recentRuns.find((item) => item.id === recentRunId);

  if (!recentRun || Boolean(recentRun.unavailable) === unavailable) {
    return recentRuns;
  }

  return recentRuns.map((item) =>
    item.id === recentRunId ? { ...item, unavailable } : item
  );
};

export const removeRecentRunItem = (
  recentRuns: RecentRunItem[],
  recentRunId: string
): RecentRunItem[] =>
  recentRuns.filter((recentRun) => recentRun.id !== recentRunId);

export const clearRecentRunItems = (): RecentRunItem[] => [];
