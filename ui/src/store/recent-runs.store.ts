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

import { create } from 'zustand';
import { persist } from 'zustand/middleware';

import {
  addRecentRunItem,
  clearRecentRunItems,
  DEFAULT_MAX_RECENT_RUNS,
  removeRecentRunItem,
} from '@/providers/home-data/recent-runs';
import type {
  RecentRunItem,
  RecentRunItemInput,
} from '@/providers/home-data/types';

type State = {
  recentRunsByUser: Record<string, RecentRunItem[]>;
};

type Actions = {
  recordRecentRun: (userId: string, recentRun: RecentRunItemInput) => void;
  removeRecentRun: (userId: string, recentRunId: string) => void;
  clearRecentRuns: (userId: string) => void;
};

export const RECENT_RUNS_STORAGE_NAME = 'home-recent-runs-storage';

const getUserRecentRuns = (state: State, userId: string) =>
  state.recentRunsByUser[userId] ?? [];

// The local store intentionally only tracks runs started from this browser UI.
// Runs started via CLI, API, or another browser are outside this temporary
// local-storage implementation until a backend-backed provider exists.
export const useRecentRunsStore = create<State & Actions>()(
  persist(
    (set) => ({
      recentRunsByUser: {},
      recordRecentRun: (userId, recentRun) =>
        set((state) => ({
          recentRunsByUser: {
            ...state.recentRunsByUser,
            [userId]: addRecentRunItem(
              getUserRecentRuns(state, userId),
              recentRun,
              DEFAULT_MAX_RECENT_RUNS
            ),
          },
        })),
      removeRecentRun: (userId, recentRunId) =>
        set((state) => ({
          recentRunsByUser: {
            ...state.recentRunsByUser,
            [userId]: removeRecentRunItem(
              getUserRecentRuns(state, userId),
              recentRunId
            ),
          },
        })),
      clearRecentRuns: (userId) =>
        set((state) => ({
          recentRunsByUser: {
            ...state.recentRunsByUser,
            [userId]: clearRecentRunItems(),
          },
        })),
    }),
    {
      name: RECENT_RUNS_STORAGE_NAME,
      partialize: (state) => ({ recentRunsByUser: state.recentRunsByUser }),
      version: 2,
    }
  )
);
