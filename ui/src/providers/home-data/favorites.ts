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

import type { FavoriteItem, FavoriteItemInput, FavoriteType } from './types';

export const DEFAULT_FAVORITE_GROUP_ORDER: FavoriteType[] = [
  'run',
  'repository',
  'product',
  'organization',
];

/** Clean up a saved favorite group order so it contains each known group once. */
export const normalizeFavoriteGroupOrder = (
  favoriteGroupOrder: readonly FavoriteType[] | undefined
): FavoriteType[] => {
  const knownFavoriteTypes = new Set(DEFAULT_FAVORITE_GROUP_ORDER);
  const orderedFavoriteTypes = favoriteGroupOrder?.filter((type) =>
    knownFavoriteTypes.has(type)
  );

  return [
    ...new Set(orderedFavoriteTypes),
    ...DEFAULT_FAVORITE_GROUP_ORDER.filter(
      (type) => !orderedFavoriteTypes?.includes(type)
    ),
  ];
};

export const buildFavoriteId = (
  type: FavoriteType,
  ...ids: Array<number | string>
) => `${type}:${ids.map((id) => id.toString()).join(':')}`;

export const buildOrganizationFavoriteId = (organizationId: number | string) =>
  buildFavoriteId('organization', organizationId);

export const buildProductFavoriteId = (
  organizationId: number | string,
  productId: number | string
) => buildFavoriteId('product', organizationId, productId);

export const buildRepositoryFavoriteId = (
  organizationId: number | string,
  productId: number | string,
  repositoryId: number | string
) => buildFavoriteId('repository', organizationId, productId, repositoryId);

export const buildRunFavoriteId = (runId: number | string) =>
  buildFavoriteId('run', runId);

/** Create a stored favorite item and assign a timestamp if none is provided. */
export const createFavoriteItem = (
  favorite: FavoriteItemInput,
  now = new Date()
): FavoriteItem => ({
  ...favorite,
  breadcrumbs: [...favorite.breadcrumbs],
  starredAt: favorite.starredAt ?? now.toISOString(),
});

/** Add or replace a favorite, keeping the newest entry first. */
export const addFavoriteItem = (
  favorites: FavoriteItem[],
  favorite: FavoriteItemInput,
  now = new Date()
): FavoriteItem[] => {
  const item = createFavoriteItem(favorite, now);

  return [item, ...favorites.filter((existing) => existing.id !== item.id)];
};

/** Update an existing favorite without adding it if it is not already stored. */
export const updateFavoriteItem = (
  favorites: FavoriteItem[],
  favorite: FavoriteItemInput,
  now = new Date()
): FavoriteItem[] => {
  if (!favorites.some((existing) => existing.id === favorite.id)) {
    return favorites;
  }

  const item = createFavoriteItem(favorite, now);

  return favorites.map((existing) =>
    existing.id === item.id ? item : existing
  );
};

export const removeFavoriteItem = (
  favorites: FavoriteItem[],
  favoriteId: string
): FavoriteItem[] => favorites.filter((favorite) => favorite.id !== favoriteId);

/** Remove an existing favorite or add it if it is not stored yet. */
export const toggleFavoriteItem = (
  favorites: FavoriteItem[],
  favorite: FavoriteItemInput,
  now = new Date()
): FavoriteItem[] => {
  if (favorites.some((existing) => existing.id === favorite.id)) {
    return removeFavoriteItem(favorites, favorite.id);
  }

  return addFavoriteItem(favorites, favorite, now);
};

if (import.meta.vitest) {
  const { describe, expect, it } = import.meta.vitest;

  describe('favorite helpers', () => {
    const now = new Date('2026-06-16T12:00:00.000Z');

    const favorite: FavoriteItemInput = {
      id: buildOrganizationFavoriteId(1),
      type: 'organization',
      name: 'Acme',
      breadcrumbs: ['Acme'],
      to: '/organizations/$orgId',
      params: { orgId: '1' },
    };

    it('normalizes favorite group order', () => {
      expect(
        normalizeFavoriteGroupOrder(['product', 'run', 'product'])
      ).toStrictEqual(['product', 'run', 'repository', 'organization']);
    });

    it('builds stable favorite IDs', () => {
      expect(buildOrganizationFavoriteId(123)).toBe('organization:123');
      expect(buildProductFavoriteId(123, 456)).toBe('product:123:456');
      expect(buildRepositoryFavoriteId(123, 456, 789)).toBe(
        'repository:123:456:789'
      );
      expect(buildRunFavoriteId(42)).toBe('run:42');
    });

    it('adds favorites newest first', () => {
      expect(addFavoriteItem([], favorite, now)).toStrictEqual([
        { ...favorite, starredAt: now.toISOString() },
      ]);
    });

    it('de-duplicates favorites by ID', () => {
      const first = addFavoriteItem([], favorite, now);
      const updated = addFavoriteItem(
        first,
        { ...favorite, name: 'Acme Updated' },
        new Date('2026-06-16T13:00:00.000Z')
      );

      expect(updated).toStrictEqual([
        {
          ...favorite,
          name: 'Acme Updated',
          starredAt: '2026-06-16T13:00:00.000Z',
        },
      ]);
    });

    it('updates a favorite in place', () => {
      const favorites = addFavoriteItem([], favorite, now);

      expect(
        updateFavoriteItem(favorites, {
          ...favorite,
          name: 'Acme Updated',
          starredAt: now.toISOString(),
        })
      ).toStrictEqual([
        {
          ...favorite,
          name: 'Acme Updated',
          starredAt: now.toISOString(),
        },
      ]);
    });

    it('removes favorites by ID', () => {
      const favorites = addFavoriteItem([], favorite, now);

      expect(removeFavoriteItem(favorites, favorite.id)).toStrictEqual([]);
    });

    it('toggles favorites', () => {
      const favorites = toggleFavoriteItem([], favorite, now);

      expect(favorites).toStrictEqual([
        { ...favorite, starredAt: now.toISOString() },
      ]);
      expect(toggleFavoriteItem(favorites, favorite, now)).toStrictEqual([]);
    });
  });
}
