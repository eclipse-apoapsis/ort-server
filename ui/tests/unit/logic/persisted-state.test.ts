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

import { describe, expect, it } from 'vitest';

import { DEFAULT_FAVORITE_GROUP_ORDER } from '@/providers/home-data/favorites';
import {
  migrateFavoritesState,
  migrateRecentRunsState,
} from '@/providers/home-data/persisted-state';

const EPOCH = new Date(0).toISOString();

const recentRun = (overrides: Record<string, unknown> = {}) => ({
  id: 'run:1',
  runId: 1,
  runIndex: 11,
  organizationId: 2,
  organizationName: 'Acme',
  productId: 3,
  productName: 'Product',
  repositoryId: 4,
  repositoryName: 'Repository',
  revision: 'main',
  status: 'FINISHED',
  to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex',
  params: {
    orgId: '2',
    productId: '3',
    repoId: '4',
    runIndex: '11',
  },
  createdAt: '2026-06-16T12:00:00.000Z',
  recordedAt: '2026-06-16T12:00:01.000Z',
  ...overrides,
});

const favorite = (overrides: Record<string, unknown> = {}) => ({
  id: 'product:2:3',
  type: 'product',
  name: 'Product',
  breadcrumbs: ['Acme', 'Product'],
  to: '/organizations/$orgId/products/$productId',
  params: { orgId: '2', productId: '3' },
  starredAt: '2026-06-16T12:00:00.000Z',
  ...overrides,
});

const recentRunsState = (...recentRuns: unknown[]) => ({
  recentRunsByUser: { user: recentRuns },
});

const favoritesState = (...favorites: unknown[]) => ({
  favoritesByUser: { user: favorites },
  favoriteGroupOrderByUser: {},
});

describe('migrateRecentRunsState', () => {
  it('keeps valid entries stored by an unknown version', () => {
    const state = migrateRecentRunsState(recentRunsState(recentRun()));

    expect(state.recentRunsByUser.user).toStrictEqual([recentRun()]);
  });

  it('drops malformed entries but keeps their valid siblings', () => {
    const state = migrateRecentRunsState(
      recentRunsState(
        recentRun(),
        { id: 'run:2' },
        null,
        'run:3',
        recentRun({ id: 'run:4', runId: 4, runIndex: 'not-a-number' })
      )
    );

    expect(state.recentRunsByUser.user).toStrictEqual([recentRun()]);
  });

  it('returns the empty default for payloads that are not usable', () => {
    expect(migrateRecentRunsState(null).recentRunsByUser).toStrictEqual({});
    expect(
      migrateRecentRunsState('recent runs').recentRunsByUser
    ).toStrictEqual({});
    expect(
      migrateRecentRunsState({ favorites: [] }).recentRunsByUser
    ).toStrictEqual({});
  });

  it('drops users without any usable entry', () => {
    const state = migrateRecentRunsState({
      recentRunsByUser: { user: [recentRun()], other: [{ id: 'run:2' }] },
    });

    expect(Object.keys(state.recentRunsByUser)).toStrictEqual(['user']);
  });

  it('backfills a missing timestamp from the creation time', () => {
    const state = migrateRecentRunsState(
      recentRunsState(recentRun({ recordedAt: undefined }))
    );

    expect(state.recentRunsByUser.user?.[0]?.recordedAt).toBe(
      '2026-06-16T12:00:00.000Z'
    );
  });

  it('falls back to the epoch when no timestamp is stored', () => {
    const state = migrateRecentRunsState(
      recentRunsState(
        recentRun({ createdAt: undefined, recordedAt: undefined })
      )
    );

    expect(state.recentRunsByUser.user?.[0]?.recordedAt).toBe(EPOCH);
  });

  it('rebuilds the identity, route and route parameters when they are missing', () => {
    const state = migrateRecentRunsState(
      recentRunsState(
        recentRun({ id: undefined, to: undefined, params: undefined })
      )
    );

    expect(state.recentRunsByUser.user?.[0]).toMatchObject({
      id: 'run:1',
      to: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex',
      params: {
        orgId: '2',
        productId: '3',
        repoId: '4',
        runIndex: '11',
      },
    });
  });

  it('drops unusable optional values instead of the whole entry', () => {
    const state = migrateRecentRunsState(
      recentRunsState(
        recentRun({ status: 'NO_SUCH_STATUS', revision: 42, path: {} })
      )
    );

    expect(state.recentRunsByUser.user?.[0]).toMatchObject({
      id: 'run:1',
      status: undefined,
      revision: undefined,
      path: undefined,
    });
  });

  it('keeps unknown fields so that a newer shape survives a downgrade', () => {
    const state = migrateRecentRunsState(
      recentRunsState(recentRun({ unavailable: true }))
    );

    expect(state.recentRunsByUser.user?.[0]).toMatchObject({
      unavailable: true,
    });
  });
});

describe('migrateFavoritesState', () => {
  it('keeps valid entries stored by an unknown version', () => {
    const state = migrateFavoritesState(favoritesState(favorite()));

    expect(state.favoritesByUser.user).toStrictEqual([favorite()]);
  });

  it('drops malformed entries but keeps their valid siblings', () => {
    const state = migrateFavoritesState(
      favoritesState(
        favorite(),
        favorite({ id: 'thing:1', type: 'thing' }),
        favorite({ id: '', name: '' }),
        undefined
      )
    );

    expect(state.favoritesByUser.user).toStrictEqual([favorite()]);
  });

  it('returns the empty default for payloads that are not usable', () => {
    const state = migrateFavoritesState(null);

    expect(state.favoritesByUser).toStrictEqual({});
    expect(state.favoriteGroupOrderByUser).toStrictEqual({});
  });

  it('backfills missing breadcrumbs and the starring time', () => {
    const state = migrateFavoritesState(
      favoritesState(favorite({ breadcrumbs: undefined, starredAt: undefined }))
    );

    expect(state.favoritesByUser.user?.[0]).toMatchObject({
      breadcrumbs: ['Product'],
      starredAt: EPOCH,
    });
  });

  it('normalizes an unknown group type out of the group order', () => {
    const state = migrateFavoritesState({
      favoritesByUser: {},
      favoriteGroupOrderByUser: {
        user: ['thing', 'product', 'product', 'organization'],
      },
    });

    expect(state.favoriteGroupOrderByUser.user).toStrictEqual([
      'product',
      'organization',
      'run',
      'repository',
    ]);
  });

  it('falls back to the default group order for an unusable value', () => {
    const state = migrateFavoritesState({
      favoritesByUser: {},
      favoriteGroupOrderByUser: { user: 'product' },
    });

    expect(state.favoriteGroupOrderByUser.user).toStrictEqual(
      DEFAULT_FAVORITE_GROUP_ORDER
    );
  });

  it('migrates the favorites of one store even if the other one is broken', () => {
    const state = migrateFavoritesState({
      favoritesByUser: { user: [favorite()] },
      favoriteGroupOrderByUser: 'not-a-record',
    });

    expect(state.favoritesByUser.user).toStrictEqual([favorite()]);
    expect(state.favoriteGroupOrderByUser).toStrictEqual({});
  });
});
