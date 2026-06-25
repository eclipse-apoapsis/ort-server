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
  addFavoriteItem,
  buildOrganizationFavoriteId,
  buildProductFavoriteId,
  buildRepositoryFavoriteId,
  buildRunFavoriteId,
  normalizeFavoriteGroupOrder,
  removeFavoriteItem,
  toggleFavoriteItem,
  updateFavoriteItem,
} from '@/providers/home-data/favorites';
import type { FavoriteItemInput } from '@/providers/home-data/types';

const now = new Date('2026-06-16T12:00:00.000Z');

const favorite: FavoriteItemInput = {
  id: buildOrganizationFavoriteId(1),
  type: 'organization',
  name: 'Acme',
  breadcrumbs: ['Acme'],
  to: '/organizations/$orgId',
  params: { orgId: '1' },
};

it('adds favorites newest first', () => {
  expect(addFavoriteItem([], favorite, now)).toStrictEqual([
    { ...favorite, starredAt: now.toISOString() },
  ]);
});

it('builds stable favorite IDs', () => {
  expect(buildOrganizationFavoriteId(123)).toBe('organization:123');
  expect(buildProductFavoriteId(123, 456)).toBe('product:123:456');
  expect(buildRepositoryFavoriteId(123, 456, 789)).toBe(
    'repository:123:456:789'
  );
  expect(buildRunFavoriteId(42)).toBe('run:42');
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

it('normalizes favorite group order', () => {
  expect(
    normalizeFavoriteGroupOrder(['product', 'run', 'product'])
  ).toStrictEqual(['product', 'run', 'repository', 'organization']);
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
