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

import type { OrtRunStatus } from '@/api';

export type FavoriteType = 'organization' | 'product' | 'repository' | 'run';

export type FavoriteRouteParams = Record<string, string>;

export type FavoriteItem = {
  id: string;
  type: FavoriteType;
  name: string;
  breadcrumbs: string[];
  to: string;
  params?: FavoriteRouteParams;
  starredAt: string;
};

export type FavoriteItemInput = Omit<FavoriteItem, 'starredAt'> & {
  starredAt?: string;
};

export type RecentRunRouteParams = Record<string, string>;

export type RecentRunItem = {
  id: string;
  runId: number;
  runIndex: number;
  organizationId: number;
  organizationName: string;
  productId: number;
  productName: string;
  repositoryId: number;
  repositoryName: string;
  revision?: string;
  path?: string;
  status?: OrtRunStatus;
  to: string;
  params: RecentRunRouteParams;
  createdAt?: string;
  recordedAt: string;
};

export type RecentRunItemInput = Omit<RecentRunItem, 'recordedAt'> & {
  recordedAt?: string;
};

export type HomeFavoriteActions = {
  addFavorite: (favorite: FavoriteItemInput) => void;
  updateFavorite: (favorite: FavoriteItemInput) => void;
  removeFavorite: (favoriteId: string) => void;
  toggleFavorite: (favorite: FavoriteItemInput) => void;
  setFavoriteGroupOrder: (favoriteGroupOrder: FavoriteType[]) => void;
};

export type HomeRecentRunActions = {
  recordRecentRun: (recentRun: RecentRunItemInput) => void;
  removeRecentRun: (recentRunId: string) => void;
  clearRecentRuns: () => void;
};

export type HomeDataProviderValue = {
  /** Whether home data can currently be read and written (e.g. a user is known). */
  useIsEnabled: () => boolean;
  useFavorites: () => FavoriteItem[];
  useFavoriteGroupOrder: () => FavoriteType[];
  useIsFavorite: (favoriteId: string) => boolean;
  useFavoriteActions: () => HomeFavoriteActions;
  useRecentRuns: () => RecentRunItem[];
  useRecentRunActions: () => HomeRecentRunActions;
};
