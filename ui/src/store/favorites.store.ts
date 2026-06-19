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
  addFavoriteItem,
  normalizeFavoriteGroupOrder,
  removeFavoriteItem,
  toggleFavoriteItem,
  updateFavoriteItem,
} from '@/providers/home-data/favorites';
import type {
  FavoriteItem,
  FavoriteItemInput,
  FavoriteType,
} from '@/providers/home-data/types';

type State = {
  favoritesByUser: Record<string, FavoriteItem[]>;
  favoriteGroupOrderByUser: Record<string, FavoriteType[]>;
};

type Actions = {
  addFavorite: (userId: string, favorite: FavoriteItemInput) => void;
  updateFavorite: (userId: string, favorite: FavoriteItemInput) => void;
  removeFavorite: (userId: string, favoriteId: string) => void;
  toggleFavorite: (userId: string, favorite: FavoriteItemInput) => void;
  clearFavorites: (userId: string) => void;
  setFavoriteGroupOrder: (
    userId: string,
    favoriteGroupOrder: FavoriteType[]
  ) => void;
};

export const FAVORITES_STORAGE_NAME = 'home-favorites-storage';

const getUserFavorites = (state: State, userId: string) =>
  state.favoritesByUser[userId] ?? [];

export const useFavoritesStore = create<State & Actions>()(
  persist(
    (set) => ({
      favoritesByUser: {},
      favoriteGroupOrderByUser: {},
      addFavorite: (userId, favorite) =>
        set((state) => ({
          favoritesByUser: {
            ...state.favoritesByUser,
            [userId]: addFavoriteItem(
              getUserFavorites(state, userId),
              favorite
            ),
          },
        })),
      updateFavorite: (userId, favorite) =>
        set((state) => ({
          favoritesByUser: {
            ...state.favoritesByUser,
            [userId]: updateFavoriteItem(
              getUserFavorites(state, userId),
              favorite
            ),
          },
        })),
      removeFavorite: (userId, favoriteId) =>
        set((state) => ({
          favoritesByUser: {
            ...state.favoritesByUser,
            [userId]: removeFavoriteItem(
              getUserFavorites(state, userId),
              favoriteId
            ),
          },
        })),
      toggleFavorite: (userId, favorite) =>
        set((state) => ({
          favoritesByUser: {
            ...state.favoritesByUser,
            [userId]: toggleFavoriteItem(
              getUserFavorites(state, userId),
              favorite
            ),
          },
        })),
      clearFavorites: (userId) =>
        set((state) => ({
          favoritesByUser: {
            ...state.favoritesByUser,
            [userId]: [],
          },
        })),
      setFavoriteGroupOrder: (userId, favoriteGroupOrder) =>
        set((state) => ({
          favoriteGroupOrderByUser: {
            ...state.favoriteGroupOrderByUser,
            [userId]: normalizeFavoriteGroupOrder(favoriteGroupOrder),
          },
        })),
    }),
    {
      name: FAVORITES_STORAGE_NAME,
      partialize: (state) => ({
        favoritesByUser: state.favoritesByUser,
        favoriteGroupOrderByUser: state.favoriteGroupOrderByUser,
      }),
      version: 2,
    }
  )
);
