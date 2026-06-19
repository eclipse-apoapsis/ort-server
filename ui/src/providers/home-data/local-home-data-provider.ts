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

import { useMemo } from 'react';

import { useFavoritesStore } from '@/store/favorites.store';
import { useRecentRunsStore } from '@/store/recent-runs.store';
import { DEFAULT_FAVORITE_GROUP_ORDER } from './favorites';
import type {
  FavoriteItem,
  FavoriteItemInput,
  FavoriteType,
  HomeDataProviderValue,
  HomeFavoriteActions,
  HomeRecentRunActions,
  RecentRunItem,
  RecentRunItemInput,
} from './types';

const EMPTY_HOME_DATA_USER_ID = '';
const EMPTY_FAVORITES: FavoriteItem[] = [];
const EMPTY_RECENT_RUNS: RecentRunItem[] = [];

const isHomeDataUserAvailable = (
  userId: string | undefined
): userId is string => Boolean(userId);

/** Create a home data provider backed by user-scoped local storage. */
export const createLocalHomeDataProvider = (
  userId: string | undefined
): HomeDataProviderValue => {
  const scopedUserId = userId ?? EMPTY_HOME_DATA_USER_ID;

  const useLocalIsEnabled = () => isHomeDataUserAvailable(userId);

  const useLocalFavorites = () =>
    useFavoritesStore(
      (state) => state.favoritesByUser[scopedUserId] ?? EMPTY_FAVORITES
    );

  const useLocalFavoriteGroupOrder = () =>
    useFavoritesStore(
      (state) =>
        state.favoriteGroupOrderByUser[scopedUserId] ??
        DEFAULT_FAVORITE_GROUP_ORDER
    );

  const useLocalIsFavorite = (favoriteId: string) =>
    useFavoritesStore((state) =>
      (state.favoritesByUser[scopedUserId] ?? []).some(
        (favorite) => favorite.id === favoriteId
      )
    );

  const useLocalFavoriteActions = () => {
    const currentUserId = userId;
    const addFavorite = useFavoritesStore((state) => state.addFavorite);
    const updateFavorite = useFavoritesStore((state) => state.updateFavorite);
    const removeFavorite = useFavoritesStore((state) => state.removeFavorite);
    const toggleFavorite = useFavoritesStore((state) => state.toggleFavorite);
    const setFavoriteGroupOrder = useFavoritesStore(
      (state) => state.setFavoriteGroupOrder
    );

    return useMemo<HomeFavoriteActions>(
      () => ({
        addFavorite: (favorite: FavoriteItemInput) => {
          if (isHomeDataUserAvailable(currentUserId)) {
            addFavorite(currentUserId, favorite);
          }
        },
        updateFavorite: (favorite: FavoriteItemInput) => {
          if (isHomeDataUserAvailable(currentUserId)) {
            updateFavorite(currentUserId, favorite);
          }
        },
        removeFavorite: (favoriteId: string) => {
          if (isHomeDataUserAvailable(currentUserId)) {
            removeFavorite(currentUserId, favoriteId);
          }
        },
        toggleFavorite: (favorite: FavoriteItemInput) => {
          if (isHomeDataUserAvailable(currentUserId)) {
            toggleFavorite(currentUserId, favorite);
          }
        },
        setFavoriteGroupOrder: (favoriteGroupOrder: FavoriteType[]) => {
          if (isHomeDataUserAvailable(currentUserId)) {
            setFavoriteGroupOrder(currentUserId, favoriteGroupOrder);
          }
        },
      }),
      [
        addFavorite,
        currentUserId,
        removeFavorite,
        setFavoriteGroupOrder,
        toggleFavorite,
        updateFavorite,
      ]
    );
  };

  const useLocalRecentRuns = () =>
    useRecentRunsStore(
      (state) => state.recentRunsByUser[scopedUserId] ?? EMPTY_RECENT_RUNS
    );

  const useLocalRecentRunActions = () => {
    const currentUserId = userId;
    const recordRecentRun = useRecentRunsStore(
      (state) => state.recordRecentRun
    );
    const removeRecentRun = useRecentRunsStore(
      (state) => state.removeRecentRun
    );
    const clearRecentRuns = useRecentRunsStore(
      (state) => state.clearRecentRuns
    );

    return useMemo<HomeRecentRunActions>(
      () => ({
        recordRecentRun: (recentRun: RecentRunItemInput) => {
          if (isHomeDataUserAvailable(currentUserId)) {
            recordRecentRun(currentUserId, recentRun);
          }
        },
        removeRecentRun: (recentRunId: string) => {
          if (isHomeDataUserAvailable(currentUserId)) {
            removeRecentRun(currentUserId, recentRunId);
          }
        },
        clearRecentRuns: () => {
          if (isHomeDataUserAvailable(currentUserId)) {
            clearRecentRuns(currentUserId);
          }
        },
      }),
      [clearRecentRuns, currentUserId, recordRecentRun, removeRecentRun]
    );
  };

  return {
    useIsEnabled: useLocalIsEnabled,
    useFavorites: useLocalFavorites,
    useFavoriteGroupOrder: useLocalFavoriteGroupOrder,
    useIsFavorite: useLocalIsFavorite,
    useFavoriteActions: useLocalFavoriteActions,
    useRecentRuns: useLocalRecentRuns,
    useRecentRunActions: useLocalRecentRunActions,
  };
};

export const localHomeDataProvider = createLocalHomeDataProvider(undefined);
